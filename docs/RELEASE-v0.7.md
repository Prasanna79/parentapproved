# ParentApproved.tv v0.7.0 "The Clock" — Release Notes

**Date:** February 20, 2026
**Milestone:** Parents control how much kids watch, not just what they watch.

```
The clock ticks on —
gentle hands that guard the hours,
screen fades, stars come out.
```

---

## What's New

### Daily Time Limits
- Set a maximum daily watch time (applies to all days in v0.7, per-day-of-week in v0.7.1)
- Only active playback counts — paused time is free
- 5-minute warning overlay appears in the corner before time runs out
- When the limit is reached, playback stops and the lock screen appears

### Bedtime
- Set start/end times when the TV app is unavailable (e.g., 8:30 PM — 7:00 AM)
- Spans midnight correctly
- During bedtime, the lock screen appears immediately on app launch or during playback

### Lock Screen
- Full-screen overlay blocks all interaction when time is up, bedtime is active, or parent locks manually
- Reason-specific messages: "All done for today!", "Time for bed!", "Taking a break!"
- Back button does nothing — only the parent dashboard can unlock
- Survives Home button press — re-checks on every app resume
- "Request More Time" button sends a signal to the parent dashboard (2-minute cooldown)
- Polls every 5 seconds — TV unlocks within seconds of parent action

### Instant Lock / Unlock
- Lock or unlock the TV immediately from the dashboard
- Lock stops any active playback instantly via PlaybackCommandBus
- Manual lock overrides all other rules — only explicit unlock clears it

### Bonus Time
- Grant +15 or +30 minutes from the dashboard
- Bonus extends today's daily limit and overrides bedtime
- Multiple grants accumulate (capped at 4 hours)
- Resets at midnight — no carryover

### Dashboard "Screen Time" Section
- New section between Now Playing and Content Sources
- Shows today's usage, remaining time, and current status
- Lock/Unlock toggle button
- Bonus time buttons (visible when locked or running low)
- "Edit Limits" modal for setting daily limit and bedtime

### Security Hardening
- Debug intents gated on `BuildConfig.IS_DEBUG` — disabled in release builds
- PIN brute-force lockout counters persisted to SharedPreferences — survive force-kill
- Watch time flushed to Room DB on `onStop()` — force-kill loses at most ~10 seconds

---

## Architecture

### New: `timelimits/` package
- `TimeLimitManager` — central brain answering "can the TV play right now?"
- `TimeLimitConfig` — data class: daily limits, bedtime, manual lock, bonus
- `TimeLimitStatus` — sealed class: `Allowed`, `Warning(minutesLeft)`, `Blocked(reason)`
- `TimeLimitStore` / `RoomTimeLimitStore` — Room persistence
- `WatchTimeProvider` / `RoomWatchTimeProvider` — today's watch time from DB + current video
- `TimeLimitDao` + `TimeLimitConfigEntity` — Room entity, singleton row pattern

### New: `LockScreen.kt`
- Compose screen in nav graph at `lock/{reason}`
- Intercepts all keys via `onKeyEvent`
- Polls `canPlay()` every 5 seconds for auto-unlock

### New: `TimeLimitRoutes.kt`
- `GET /time-limits` — current config + today's status
- `PUT /time-limits` — update daily limits and bedtime
- `POST /time-limits/lock` — manual lock/unlock
- `POST /time-limits/bonus` — grant bonus minutes
- `POST /time-limits/request` — child requests more time (unauthenticated, rate-limited)

### Modified
- `PlaybackScreen` — pre-play check, 30-second periodic check, warning overlay
- `HomeScreen` — `canPlay()` check on launch, every 5 seconds
- `AppNavigation` — lock route with `popUpTo(HOME)` to clear back stack
- `DebugReceiver` — 8 new debug intents for time limit testing
- `CacheDatabase` — version 3 → 4, additive migration (new table)
- `MainActivity` — `onStop()` flushes current video elapsed to Room
- `PinManager` — lockout persistence via `SharedPrefsPinLockoutPersistence`

---

## Files Changed

| Category | Files |
|----------|-------|
| **New — timelimits** | `TimeLimitManager.kt`, `TimeLimitConfig.kt`, `TimeLimitStatus.kt`, `LockReason.kt`, `TimeLimitStore.kt`, `WatchTimeProvider.kt`, `RoomTimeLimitStore.kt`, `RoomWatchTimeProvider.kt` |
| **New — Room** | `TimeLimitConfigEntity.kt`, `TimeLimitDao.kt` |
| **New — server** | `TimeLimitRoutes.kt` |
| **New — UI** | `LockScreen.kt` |
| **New — auth** | `SharedPrefsPinLockoutPersistence.kt` |
| **New — tests** | `TimeLimitManagerTest.kt` (35+), `TimeLimitDaoTest.kt` (7), `TimeLimitRoutesTest.kt` (22), `WatchTimeProviderTest.kt` (3), `PinManagerTest.kt` additions |
| **Modified — app** | `CacheDatabase.kt`, `ServiceLocator.kt`, `ParentApprovedServer.kt`, `MainActivity.kt`, `PlayEventRecorder.kt` |
| **Modified — UI** | `PlaybackScreen.kt`, `HomeScreen.kt`, `AppNavigation.kt` |
| **Modified — debug** | `DebugReceiver.kt`, `AndroidManifest.xml` |
| **Modified — dashboard** | `index.html`, `app.js`, `style.css` |
| **Modified — tests** | `test-suite.sh`, `ui-test.sh` |
| **Modified — build** | `build.gradle.kts` (v0.6.2 → v0.7.0) |

---

## Test Coverage

| Suite | Count | Status |
|-------|-------|--------|
| Unit tests | 265 | All pass |
| Intent + HTTP (test-suite.sh) | 15 | All pass |
| UI tests (ui-test.sh) | 54 | All pass |
| **Total** | **334** | **All pass** |

---

## Deferred to v0.7.1+

- Per-day-of-week daily limits (7-day grid in dashboard)
- Per-day-of-week bedtime schedules
- Per-playlist time limits
- HTTPS / self-signed TLS
- CORS headers
- Session audit log
