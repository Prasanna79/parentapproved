# ParentApproved V0.7 — Product Spec: "The Clock"

*Parents set the rules. The TV enforces them.*

---

## Problem

ParentApproved lets parents curate *what* kids watch but not *how much*. A child can watch approved content all day. Parents currently have no way to set time limits, enforce bedtime, or stop the TV remotely without picking up their phone every time. Google Family Link solves this at the device level — we need it at the app level, tuned for a TV.

---

## What's In v0.7

### 1. Daily Time Limit (P0)

Parents set a maximum daily watch time, configurable per day of the week.

**Dashboard UI — "Screen Time" section (new):**
```
┌─────────────────────────────────────────┐
│  Screen Time                            │
│                                         │
│  Daily Limits                           │
│  ┌─────────────────────────────────┐    │
│  │ Mon  Tue  Wed  Thu  Fri  Sat  Sun│   │
│  │ 1h   1h   1h   1h   1h   2h  2h │   │
│  └─────────────────────────────────┘    │
│                                         │
│  Today: 47m / 1h 0m remaining          │
│  [Edit Limits]                          │
└─────────────────────────────────────────┘
```

**Edit modal — per-day controls:**
- Each day of the week: slider or stepper, 15-minute increments, range 15m–8h
- "Same every day" toggle copies one value to all days
- "No limit" toggle per day (disables limit for that day)
- Save applies immediately

**How it works on the TV:**
1. `TimeLimitManager` checks remaining time before each video starts
2. During playback, a periodic check (every 30 seconds) compares accumulated daily watch time against the limit
3. **5 minutes before limit**: subtle overlay appears in corner — "5 minutes left!"
4. **When limit reached**: playback stops, TV shows lock screen

**Time accounting:**
- Only active playback counts (paused time does not)
- Uses existing `PlayEventDao.sumDurationToday()` + current video elapsed
- Resets at midnight local time
- If a video is playing at midnight, the timer resets (fresh day)

---

### 2. Downtime / Bedtime (P0)

Parents set hours when the TV app is unavailable, per day of the week.

**Dashboard UI — inside "Screen Time" section:**
```
┌─────────────────────────────────────────┐
│  Bedtime                                │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Every day:  8:30 PM — 7:00 AM  │    │
│  └─────────────────────────────────┘    │
│  [Edit Schedule]                        │
└─────────────────────────────────────────┘
```

**Edit modal — per-day schedule:**
- Start time and end time per day (or "same every day" toggle)
- Time pickers in 15-minute increments
- Can span midnight (e.g., 8:30 PM → 7:00 AM)
- "Off" toggle disables bedtime for a specific day

**How it works on the TV:**
1. When the app launches during downtime → lock screen immediately
2. When downtime starts during playback → stop playback, show lock screen
3. `TimeLimitManager` checks downtime on every video start and periodically (every 30 seconds)
4. Downtime is independent of daily limit — it's a hard schedule

---

### 3. Lock Screen (P0)

A friendly full-screen overlay shown when time is up, bedtime is active, or the parent locks manually.

```
┌─────────────────────────────────────────┐
│                                         │
│            🌙                           │
│                                         │
│       Time for a break!                 │
│                                         │
│    Ask your parent to unlock.           │
│                                         │
│                                         │
│         [ Request More Time ]           │
│                                         │
└─────────────────────────────────────────┘
```

**Lock screen states** (different messages):
| Reason | Title | Subtitle |
|--------|-------|----------|
| Daily limit reached | "All done for today!" | "You watched {time}. See you tomorrow!" |
| Bedtime active | "Time for bed!" | "TV time starts again at {start_time}." |
| Parent locked | "Taking a break!" | "Ask your parent to unlock." |

**Behavior:**
- Full-screen Compose overlay, covers entire app
- No D-pad navigation can dismiss it (Back button does nothing)
- Only unlockable by: parent dashboard action, bonus time, or schedule change
- "Request More Time" button (optional, see §6) sends a ping to the parent dashboard
- Moon/sun icon changes based on lock reason
- The lock screen is a Compose screen in the nav graph, navigated to when lock triggers

---

### 4. Bonus Time (P1)

Parents can grant extra minutes for today without changing the weekly schedule.

**Dashboard UI — visible when time is running low or device is locked:**
```
┌─────────────────────────────────────────┐
│  ⏰ Time's up! TV is locked.            │
│                                         │
│  Give bonus time:                       │
│  [ +15 min ]  [ +30 min ]  [ +1 hour ] │
│                                         │
│  Or set a custom amount: [___] minutes  │
└─────────────────────────────────────────┘
```

**How it works:**
- Bonus time adds minutes to today's daily limit only
- Takes effect immediately — TV unlocks within the next poll cycle (≤5 seconds)
- Does NOT carry over to the next day
- Overrides bedtime — if bonus time is active, bedtime is suspended until bonus expires
- Multiple bonus grants accumulate (15 + 15 = 30 bonus minutes)
- Parent can see total bonus granted today
- Bonus time resets at midnight with the daily limit

**API:**
- `POST /time-limits/bonus` with `{ "minutes": 15 }`
- Response includes new remaining time

---

### 5. Instant Lock / Unlock (P0)

Parent can lock or unlock the TV immediately from the dashboard.

**Dashboard UI — in the "Screen Time" section or top bar:**
```
[ 🔒 Lock Now ]     ← when TV is unlocked
[ 🔓 Unlock ]       ← when TV is locked
```

**How it works:**
- Lock: sets a flag in `TimeLimitManager`, TV shows lock screen within ≤5 seconds (next poll)
- Unlock: clears the manual lock flag, TV returns to home screen
- Manual lock is independent of daily limit and bedtime — it's an override
- If daily limit is also exceeded, unlocking the manual lock still shows "time's up" (the underlying limit still applies)
- `PlaybackCommandBus.send(Stop)` is sent immediately on lock to halt any active playback

---

### 6. Request More Time — TV → Parent (P2)

When the lock screen is showing, the child can press a button to request more time.

**TV lock screen:**
```
[ Request More Time ]
```

**What happens:**
1. TV sends request via `POST /time-limits/request` (or via relay WebSocket)
2. Dashboard shows a notification: "Your child is requesting more time."
3. Parent can grant bonus time or ignore
4. Cooldown: button disabled for 2 minutes after pressing to prevent spam
5. No guarantee of response — the button just sends the signal

**Implementation note:** For local (same WiFi), this works via the existing polling mechanism. For relay, the WebSocket can push the request instantly.

---

### 7. Per-Playlist Time Limit (P2)

Parents can set a maximum daily time for individual playlists.

**Dashboard UI — on each playlist card:**
```
┌────────────────────────────────┐
│  Cartoon Network Playlist  ⚙️  │
│  12 videos                     │
│  Daily limit: 30 min          │
│  Today: 22m used              │
└────────────────────────────────┘
```

**How it works:**
- Optional per-playlist daily limit (default: no limit, only the global daily limit applies)
- When a playlist's limit is reached, the TV skips to the next playlist or shows the lock screen if no other playlists have time remaining
- Playlist time is a subset of the daily total (not additional)
- Uses `PlayEventDao` filtered by `playlistId`

---

## Architecture

### New: `TimeLimitManager`

Central service that answers one question: **"Can the TV play right now?"**

```
TimeLimitManager
├── canPlay(): TimeLimitStatus          // ALLOWED | WARNING(minutesLeft) | BLOCKED(reason)
├── getRemainingMinutes(): Int?         // null = no limit
├── isDowntime(): Boolean
├── isManuallyLocked(): Boolean
├── grantBonusMinutes(minutes: Int)
├── setManualLock(locked: Boolean)
├── getConfig(): TimeLimitConfig
├── updateConfig(config: TimeLimitConfig)
└── addRequestListener(callback)        // for "request more time" notifications
```

**`TimeLimitStatus`** sealed class:
```kotlin
sealed class TimeLimitStatus {
    object Allowed : TimeLimitStatus()
    data class Warning(val minutesLeft: Int) : TimeLimitStatus()
    data class Blocked(val reason: LockReason) : TimeLimitStatus()
}

enum class LockReason { DAILY_LIMIT, BEDTIME, MANUAL_LOCK, PLAYLIST_LIMIT }
```

### Storage: Room Entity

```kotlin
@Entity(tableName = "time_limit_config")
data class TimeLimitConfigEntity(
    @PrimaryKey val id: Int = 1,            // singleton row
    // Daily limits (minutes per day, -1 = no limit)
    val mondayLimitMin: Int = -1,
    val tuesdayLimitMin: Int = -1,
    val wednesdayLimitMin: Int = -1,
    val thursdayLimitMin: Int = -1,
    val fridayLimitMin: Int = -1,
    val saturdayLimitMin: Int = -1,
    val sundayLimitMin: Int = -1,
    // Bedtime (minutes from midnight, -1 = off)
    // e.g., 20:30 = 1230, 07:00 = 420
    val bedtimeStartMin: Int = -1,          // same every day (v1)
    val bedtimeEndMin: Int = -1,
    // Manual lock
    val manuallyLocked: Boolean = false,
    // Bonus time for today
    val bonusMinutes: Int = 0,
    val bonusDate: String = "",             // ISO date, resets when day changes
)
```

**Per-playlist limits** (P2, separate table):
```kotlin
@Entity(tableName = "playlist_time_limits")
data class PlaylistTimeLimitEntity(
    @PrimaryKey val playlistId: String,
    val dailyLimitMin: Int = -1,
)
```

### New Ktor Routes: `TimeLimitRoutes.kt`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/time-limits` | Yes | Get current config + today's status |
| `PUT` | `/time-limits` | Yes | Update daily limits and bedtime schedule |
| `POST` | `/time-limits/lock` | Yes | Manual lock (`{ "locked": true/false }`) |
| `POST` | `/time-limits/bonus` | Yes | Grant bonus minutes (`{ "minutes": 15 }`) |
| `POST` | `/time-limits/request` | No* | Child requests more time (from TV lock screen) |

*The `/request` endpoint is unauthenticated since it comes from the TV itself, not the parent's phone. Rate-limited to 1 per 2 minutes.

**`GET /time-limits` response:**
```json
{
  "dailyLimits": {
    "monday": 60, "tuesday": 60, "wednesday": 60,
    "thursday": 60, "friday": 60, "saturday": 120, "sunday": 120
  },
  "bedtime": { "start": "20:30", "end": "07:00" },
  "todayLimitMin": 60,
  "todayUsedMin": 47,
  "todayBonusMin": 0,
  "todayRemainingMin": 13,
  "manuallyLocked": false,
  "currentStatus": "allowed",
  "lockReason": null,
  "hasTimeRequest": false
}
```

### TV-Side Integration Points

1. **`PlaybackScreen` — before video start:**
   ```
   val status = TimeLimitManager.canPlay()
   if (status is Blocked) → navigate to LockScreen
   if (status is Warning) → show warning overlay
   ```

2. **`PlaybackScreen` — periodic check (every 30s):**
   ```
   // Inside the existing 10-second update LaunchedEffect, add a 30s time check
   if (tickCount % 3 == 0) {  // every 3rd tick = 30 seconds
       val status = TimeLimitManager.canPlay()
       if (status is Blocked) → PlaybackCommandBus.send(Stop), navigate to LockScreen
       if (status is Warning && !warningShown) → show warning overlay
   }
   ```

3. **`HomeScreen` — on launch:**
   ```
   // Check before allowing any navigation to playback
   val status = TimeLimitManager.canPlay()
   if (status is Blocked) → navigate to LockScreen
   ```

4. **`LockScreen` — new Composable:**
   - Polls `TimeLimitManager.canPlay()` every 5 seconds
   - When status changes to `Allowed` → navigate back to HomeScreen
   - "Request More Time" button calls local HTTP endpoint

5. **`AppNavigation` — new route:**
   ```
   composable("lock") { LockScreen(onUnlocked = { navController.navigate("home") }) }
   ```

### Dashboard JS Integration

New section in `app.js`:

1. **`loadTimeLimits()`** — fetches `GET /time-limits`, renders the Screen Time section
2. **`saveTimeLimits(config)`** — sends `PUT /time-limits`
3. **`toggleLock(locked)`** — sends `POST /time-limits/lock`
4. **`grantBonusTime(minutes)`** — sends `POST /time-limits/bonus`
5. **Poll for time requests** — extend `loadStatus()` to include `hasTimeRequest` flag

The Screen Time section appears between Now Playing and Playlists in the dashboard layout.

---

## Priority & Sequencing

| Priority | Feature | Effort |
|----------|---------|--------|
| **P0** | Daily time limit (same every day) | 1 day |
| **P0** | Lock screen (Compose) | 0.5 day |
| **P0** | Instant lock/unlock from dashboard | 0.5 day |
| **P0** | Bedtime (same every day) | 0.5 day |
| **P0** | Dashboard "Screen Time" UI | 1 day |
| **P0** | 5-minute warning overlay | 0.5 day |
| **P1** | Per-day-of-week limits | 0.5 day |
| **P1** | Per-day-of-week bedtime | 0.5 day |
| **P1** | Bonus time | 0.5 day |
| **P2** | Request more time (TV → parent) | 0.5 day |
| **P2** | Per-playlist limits | 1 day |

**Suggested build order:**
1. `TimeLimitManager` + Room entity + `TimeLimitDao` + ServiceLocator wiring
2. Lock screen Composable + nav integration
3. `TimeLimitRoutes.kt` (GET/PUT/lock/bonus)
4. Dashboard JS — Screen Time section
5. PlaybackScreen integration (pre-play check + periodic check)
6. HomeScreen integration (launch check)
7. 5-minute warning overlay
8. Bonus time flow
9. Per-day-of-week (upgrade from simple daily limit)
10. Per-playlist limits
11. Request more time

---

## What's NOT in v0.7

- **Schedules per-playlist** (e.g., "cartoons only before 5 PM") — future
- **Multiple child profiles** — single-child for now
- **Time limit sync across devices** — local only, one TV
- **Parental PIN to exit lock screen** — the TV remote can't unlock; only the dashboard can
- **Countdown timer always visible** — only shows in last 5 minutes

---

## Test Plan

### Unit Tests (TimeLimitManager)
- `canPlay_noLimitsConfigured_returnsAllowed`
- `canPlay_underDailyLimit_returnsAllowed`
- `canPlay_overDailyLimit_returnsBlocked`
- `canPlay_withinWarningWindow_returnsWarning`
- `canPlay_duringBedtime_returnsBlocked`
- `canPlay_bedtimeSpansMidnight_blocksCorrectly`
- `canPlay_manuallyLocked_returnsBlocked`
- `canPlay_bonusTimeExtendsLimit`
- `canPlay_bonusTimeResetsNextDay`
- `canPlay_unlockClearsManualLock_butNotDailyLimit`
- `getRemainingMinutes_accountsForCurrentVideoElapsed`

### Ktor Route Tests
- `getTimeLimits_returnsCurrentConfig`
- `putTimeLimits_updatesConfig`
- `postLock_locksDevice`
- `postUnlock_unlocksDevice`
- `postBonus_addsMinutesToToday`
- `postBonus_overridesBedtime`
- `allEndpoints_require401WithoutAuth` (except `/request`)

### Integration / E2E Tests
- Start playback → exhaust daily limit → verify playback stops and lock screen appears
- Set bedtime to now → verify lock screen appears within 30 seconds
- Lock from dashboard → verify TV shows lock screen
- Grant bonus time while locked → verify TV unlocks
- Verify lock screen survives Back button press
- Verify midnight reset clears daily accumulator

---

*The clock ticks on—*
*gentle hands that guard the hours,*
*screen fades, stars come out.*
