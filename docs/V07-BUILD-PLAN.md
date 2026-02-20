# V0.7 "The Clock" — Build Plan

TDD, phased, no code in this document. Each phase is a commit boundary.

**Review findings incorporated:** Security review (2026-02-20) + Dev manager review (2026-02-20).

---

## Phase 0: Pre-flight + security hardening

**Goal:** Green baseline. Fix critical security issues that the time controls feature makes worse.

| Step | What | How |
|------|------|-----|
| 0.1 | Run unit tests | `./gradlew testDebugUnitTest` — all pass |
| 0.2 | Run intent + HTTP tests | `scripts/test-suite.sh` — all pass |
| 0.3 | Run UI tests | `scripts/ui-test.sh` — note pre-existing flaky count |
| 0.4 | Gate debug intents on BuildConfig.DEBUG | Add early return in `DebugReceiver.onReceive()` if `!BuildConfig.IS_DEBUG`. Prevents teens from using ADB to read PIN, grant bonus, or reset state in release builds. **(Security C1)** |
| 0.5 | Persist PIN lockout counters | Move `failedAttempts`, `lockoutUntil`, `lockoutCount` to SharedPreferences so force-kill doesn't reset brute-force protection. **(Security H1)** |

Tests for 0.4:
- Existing intent tests in `test-suite.sh` continue to pass (they run against debug builds)
- Manual: verify release build ignores debug intents

Tests for 0.5:
- `pinLockout_survivesRestart` — create PinManager, fail 3 times, create new PinManager with same SharedPreferences, verify still locked out

**Commit:** `Harden debug intents and PIN lockout for v0.7`

---

## Phase 1: TimeLimitManager — pure logic, no Android

**Goal:** The core brain that answers "can the TV play right now?" — fully tested in isolation before wiring anything.

### 1.1 Define data models

Create in `tv/parentapproved/app/timelimits/`:

- `TimeLimitConfig` — data class holding daily limits (Map<DayOfWeek, Int>), bedtime start/end (minutes from midnight), manual lock flag, bonus minutes, bonus date
- `TimeLimitStatus` — sealed class: `Allowed`, `Warning(minutesLeft)`, `Blocked(reason)`
- `LockReason` — enum: `DAILY_LIMIT`, `BEDTIME`, `MANUAL_LOCK`
- `TimeLimitStore` — interface: `getConfig(): TimeLimitConfig?`, `saveConfig(config)`, `updateManualLock(locked)`, `updateBonus(minutes, date)`
- `WatchTimeProvider` — interface: `getTodayWatchSeconds(): Int`, `currentVideoElapsedProvider: () -> Long` injected as constructor param **(Dev Review: PlayEventRecorder not fakeable)**

No tests yet — these are plain data holders and interfaces.

**Design clarification (from review):** Bonus time is measured in **active-playback minutes**, same as daily limits. When bonus overrides bedtime, it creates a temporary allowance of `bonusMinutes` that counts down only during active playback. When bonus minutes are consumed, bedtime re-engages if still in the bedtime window.

### 1.2 Write TimeLimitManager tests (RED)

New file: `src/test/java/tv/parentapproved/app/timelimits/TimeLimitManagerTest.kt`

Pattern: Injectable clock `() -> Long`, `FakeTimeLimitStore` (in-memory), `FakeWatchTimeProvider` (canned values). No Room, no Android.

**Tests — all RED initially:**

Daily limit tests:
- `canPlay_noConfig_returnsAllowed`
- `canPlay_noLimitSet_returnsAllowed`
- `canPlay_underLimit_returnsAllowed`
- `canPlay_exactlyAtLimit_returnsBlocked_DAILY_LIMIT`
- `canPlay_overLimit_returnsBlocked_DAILY_LIMIT`
- `canPlay_5minRemaining_returnsWarning`
- `canPlay_6minRemaining_returnsAllowed` (warning threshold is 5 min)
- `canPlay_saturdayLimit_usedOnSaturday` (per-day-of-week routing)
- `canPlay_noLimitForToday_returnsAllowed` (day set to -1)
- `getRemainingMinutes_calculatesCorrectly`
- `getRemainingMinutes_noLimit_returnsNull`
- `getRemainingMinutes_accountsForCurrentVideoElapsed`

Bedtime tests:
- `canPlay_duringBedtime_returnsBlocked_BEDTIME`
- `canPlay_outsideBedtime_returnsAllowed`
- `canPlay_bedtimeSpansMidnight_beforeMidnight_blocked`
- `canPlay_bedtimeSpansMidnight_afterMidnight_blocked`
- `canPlay_bedtimeSpansMidnight_afterEnd_allowed`
- `canPlay_bedtimeOff_returnsAllowed`
- `canPlay_exactlyAtBedtimeStart_blocked`
- `canPlay_exactlyAtBedtimeEnd_allowed`

Manual lock tests:
- `canPlay_manuallyLocked_returnsBlocked_MANUAL_LOCK`
- `canPlay_notManuallyLocked_returnsAllowed`
- `setManualLock_true_blocksImmediately`
- `setManualLock_false_unblocks`
- `setManualLock_false_doesNotOverrideDailyLimit` (if daily limit also exceeded, still blocked)

Bonus time tests:
- `grantBonus_extendsDailyLimit`
- `grantBonus_15plus15_accumulates`
- `grantBonus_unlocksBlockedDevice`
- `grantBonus_overridesBedtime`
- `grantBonus_duringBedtime_noDaily Limit_createsTemporaryAllowance`
- `grantBonus_duringBedtime_expiresAfterActivePlayback`
- `grantBonus_resetsNextDay` (advance clock past midnight)
- `grantBonus_doesNotOverrideManualLock`
- `grantBonus_cappedAt240minutes`

Priority tests (when multiple reasons apply):
- `canPlay_manualLockPlusBedtime_reasonIsManualLock` (manual lock takes priority)
- `canPlay_dailyLimitPlusBedtime_reasonIsDailyLimit`
- `canPlay_bonusOverridesBedtime_butNotManualLock`

Midnight rollover tests **(Dev Review: automate, don't leave to manual)**:
- `midnightRollover_resetsDailyAccumulator`
- `midnightRollover_clearsBonusMinutes`
- `midnightRollover_dayStartComputedAtQueryTime` (not at construction time)

### 1.3 Implement TimeLimitManager (GREEN)

New file: `tv/parentapproved/app/timelimits/TimeLimitManager.kt`

Constructor takes:
- `clock: () -> Long` — wall clock
- `store: TimeLimitStore` — interface for load/save config
- `watchTimeProvider: WatchTimeProvider` — interface returning today's total watch seconds

Pure Kotlin, no Android imports. Run tests until all green.

### 1.4 Write FakeTimeLimitStore and FakeWatchTimeProvider

In the test file. These back the unit tests:
- `FakeTimeLimitStore` — in-memory `var config: TimeLimitConfig?`
- `FakeWatchTimeProvider` — `var todayWatchSeconds: Int = 0`, `var currentVideoElapsedMs: Long = 0`

**Commit:** `Add TimeLimitManager with 35+ unit tests`

---

## Phase 2: Room storage + ServiceLocator wiring

**Goal:** Persist time limit config to Room. Wire TimeLimitManager into DI so Phase 3 routes can use it.

**(Dev Review: merged Phase 2 + 4 because Phase 3 can't compile without ServiceLocator wiring.)**

### 2.1 Write FakeTimeLimitDao tests (RED)

New file: `src/test/java/tv/parentapproved/app/timelimits/TimeLimitDaoTest.kt`

Pattern: Same as `ChannelDaoTest.kt` — define `FakeTimeLimitDao` with in-memory storage.

Tests:
- `getConfig_empty_returnsNull`
- `insertOrUpdate_newConfig_persists`
- `insertOrUpdate_existingConfig_updates`
- `getConfig_returnsLatest`
- `grantBonus_addMinutes_accumulates`
- `setManualLock_persistsFlag`
- `clearBonusForDate_resetsWhenDateChanges`

### 2.2 Define Room entity and DAO

- `TimeLimitConfigEntity` in `data/cache/` — single-row table as described in spec
- `TimeLimitDao` interface
- Add entity to `CacheDatabase` `@Database` annotation, **bump version to 4**, add `MIGRATION_3_4` **(Dev Review: version is already 3)**
- Migration is additive: `CREATE TABLE IF NOT EXISTS time_limit_config (...)`

### 2.3 Implement RoomTimeLimitStore + RoomWatchTimeProvider

`RoomTimeLimitStore` — adapter implementing `TimeLimitStore`, delegates to `TimeLimitDao`.

`RoomWatchTimeProvider` — implements `WatchTimeProvider`, uses:
- `PlayEventDao.sumDurationToday()` for DB total
- `currentVideoElapsedProvider: () -> Long` constructor param (injected, NOT static call to `PlayEventRecorder`) **(Dev Review: singleton not fakeable)**

Tests for RoomWatchTimeProvider:
- `getTodayWatchSeconds_sumsDbPlusCurrentVideo`
- `getTodayWatchSeconds_noCurrentVideo_returnsDbOnly`
- `getTodayWatchSeconds_noDbEvents_returnsCurrentVideoOnly`

Tests for RoomTimeLimitStore:
- `roomStore_saveAndLoad_roundTrips`
- `roomStore_bonusDate_resetsOnNewDay`

### 2.4 Wire into ServiceLocator

- Add `lateinit var timeLimitManager: TimeLimitManager` to ServiceLocator
- Initialize in `init()` with real Room store and `PlayEventRecorder::getElapsedMs` as the elapsed provider
- Add to `initForTest()` as **optional parameter with default no-op implementation** so existing tests don't break **(Dev Review: blast radius)**

### 2.5 DB migration test

Instrumented test:
- `migration3to4_preservesExistingData` — verify channels, videos, play_events survive the migration

**Commit:** `Add Room storage + ServiceLocator wiring for time limits`

---

## Phase 3: Ktor routes — parent API

**Goal:** Parents can read/write time limits and lock/unlock from the dashboard.

### 3.1 Write TimeLimitRoutes tests (RED)

New file: `src/test/java/tv/parentapproved/app/server/TimeLimitRoutesTest.kt`

Pattern: Same as `StatsRoutesTest.kt` — private `testApp` helper. Pass `TimeLimitManager` directly (constructed with fakes). `SessionManager` with injectable clock.

**Note on lock-stops-playback test:** Use `launch { PlaybackCommandBus.commands.first() }` collector before the POST call to verify the Stop command. **(Dev Review)**

Tests:

GET /time-limits:
- `getTimeLimits_authenticated_returnsConfig`
- `getTimeLimits_noConfigYet_returnsDefaults`
- `getTimeLimits_unauthenticated_returns401`
- `getTimeLimits_includesTodayUsedAndRemaining`
- `getTimeLimits_includesCurrentStatus`
- `getTimeLimits_includesLockReason_whenBlocked`

PUT /time-limits:
- `putTimeLimits_validConfig_returns200`
- `putTimeLimits_updatesPersistedConfig`
- `putTimeLimits_invalidMinutes_returns400` (negative, >480)
- `putTimeLimits_unauthenticated_returns401`

POST /time-limits/lock:
- `postLock_true_locksDevice`
- `postLock_false_unlocksDevice`
- `postLock_unauthenticated_returns401`
- `postLock_stopsPlayback` (verify PlaybackCommandBus receives Stop)

POST /time-limits/bonus:
- `postBonus_addsMinutes_returns200`
- `postBonus_returnsNewRemaining`
- `postBonus_zeroMinutes_returns400`
- `postBonus_negativeMinutes_returns400`
- `postBonus_over240_returns400` (cap at 4 hours)
- `postBonus_unauthenticated_returns401`

POST /time-limits/request:
- `postRequest_noAuth_returns200` (unauthenticated — comes from TV itself)
- `postRequest_rateLimited_returns429` (second call within 2 minutes)
- `postRequest_setsHasTimeRequestFlag`

### 3.2 Implement TimeLimitRoutes.kt (GREEN)

New file: `tv/parentapproved/app/server/TimeLimitRoutes.kt`

`fun Route.timeLimitRoutes(sessionManager, timeLimitManager)` — follows existing pattern.

### 3.3 Register in ParentApprovedServer

Add `timeLimitRoutes(ServiceLocator.sessionManager, ServiceLocator.timeLimitManager)` to the routing block. (ServiceLocator wiring done in Phase 2.)

**Commit:** `Add time limit HTTP routes with 20 route tests`

---

## Phase 4: Lock screen Composable

**Goal:** A full-screen TV overlay that blocks interaction when time is up.

### 4.1 Write lock screen UI tests (RED)

Add to `scripts/ui-test.sh`:
- `test_lock_screen_shows_on_manual_lock` — send `DEBUG_MANUAL_LOCK` intent → assert UI contains "Taking a break"
- `test_lock_screen_back_button_does_nothing` — lock → press KEYCODE_BACK → assert still on lock screen
- `test_lock_screen_home_button_returns_to_lock` — lock → press KEYCODE_HOME → relaunch app → assert lock screen shown **(Security C2)**
- `test_lock_screen_unlocks_on_manual_unlock` — lock → send `DEBUG_MANUAL_UNLOCK` → assert lock screen gone, lands on home
- `test_lock_screen_shows_daily_limit_message` — use `DEBUG_SET_WATCH_TIME` to exceed limit → assert "All done for today"
- `test_lock_screen_shows_bedtime_message` — send `DEBUG_SET_BEDTIME` with current time in range → assert "Time for bed"

### 4.2 Add debug intents for time limits

New intents in `DebugReceiver` (gated on `BuildConfig.IS_DEBUG` from Phase 0):
- `DEBUG_SET_DAILY_LIMIT` — extras: `minutes` (Int)
- `DEBUG_CLEAR_DAILY_LIMIT`
- `DEBUG_MANUAL_LOCK`
- `DEBUG_MANUAL_UNLOCK`
- `DEBUG_GRANT_BONUS` — extras: `minutes` (Int)
- `DEBUG_SET_BEDTIME` — extras: `start` (HH:mm), `end` (HH:mm)
- `DEBUG_CLEAR_BEDTIME`
- `DEBUG_SET_WATCH_TIME` — extras: `seconds` (Int) — sets fake accumulated time for deterministic UI tests **(Dev Review: avoids flaky 70s waits)**
- `DEBUG_TIME_STATUS` — logs current TimeLimitManager state as JSON

Register all in AndroidManifest.xml.

### 4.3 Implement LockScreen Composable

New file: `tv/parentapproved/app/ui/screens/LockScreen.kt`

- Full-screen, `ParentBackground` color, centered content
- Icon (moon/clock/lock depending on reason), title, subtitle from lock reason
- "Request More Time" button (disabled with cooldown)
- Polls `TimeLimitManager.canPlay()` every 5 seconds — navigates away when `Allowed`
- Intercepts Back key via `onKeyEvent` — does nothing

Lock screen messages:
| Reason | Title | Subtitle |
|--------|-------|----------|
| DAILY_LIMIT | "All done for today!" | "You watched {time}. See you tomorrow!" |
| BEDTIME | "Time for bed!" | "TV time starts again at {start_time}." |
| MANUAL_LOCK | "Taking a break!" | "Ask your parent to unlock." |

### 4.4 Add to AppNavigation

New route `"lock/{reason}"` in the nav graph.

Add `onLocked: () -> Unit` callback to `PlaybackScreen` and `HomeScreen` signatures. Wire in AppNavigation to `navController.navigate("lock/$reason") { popUpTo("home") { inclusive = false } }`. **(Dev Review: no navController in screens, needs callback + popUpTo to clear playback from back stack)**

### 4.5 HomeScreen onResume check

Check `TimeLimitManager.canPlay()`:
- In `LaunchedEffect(Unit)` on initial composition
- **Also synchronously before rendering content** — if blocked, show empty Box until nav to lock completes. Prevents brief flash of home screen after Home button press. **(Security C2, Dev Review)**

### 4.6 Run UI tests (GREEN)

All new lock screen tests should pass. Run full `ui-test.sh` to confirm no regressions.

**Commit:** `Add LockScreen composable + debug intents + UI tests`

---

## Phase 5: PlaybackScreen integration

**Goal:** Playback stops automatically when time runs out, warns before limit.

### 5.1 Write playback integration tests (RED)

Add to `scripts/ui-test.sh`:
- `test_playback_stops_when_limit_reached` — set daily limit via `DEBUG_SET_DAILY_LIMIT 1` + set accumulated time via `DEBUG_SET_WATCH_TIME 55` → start playback → wait 15s (one poll cycle) → assert lock screen shown **(Dev Review: deterministic, no 70s wait)**
- `test_warning_overlay_shows` — set daily limit + set watch time to 5 min below → start playback → assert UI contains "minutes left" within 30s
- `test_playback_blocked_at_bedtime` — set bedtime to now → try to play → assert lock screen

Add to `scripts/test-suite.sh`:
- `test_http_lock_stops_playback` — start playback via intent → POST /time-limits/lock → check logcat for playback stopped
- `test_http_bonus_unlocks` — lock via HTTP → POST /time-limits/bonus 15 → check status shows allowed

### 5.2 Integrate into PlaybackScreen

Three insertion points in `PlaybackScreen.kt`:

1. **Pre-play check** — in `extractAndPlay`, before extraction: call `TimeLimitManager.canPlay()`. If `Blocked`, call `onLocked()` callback instead of playing.

2. **Periodic check — separate LaunchedEffect** with its own `delay(30_000)` loop. Independent from the 10-second update loop. **(Dev Review: don't couple to event-update cadence)** If `Blocked`, send `PlaybackCommandBus.Stop` and call `onLocked()`. If `Warning` and not yet shown, display warning overlay.

3. **Warning overlay** — a `Box` composable overlaid on the player, top-right corner, "X minutes left!" text. Auto-hides after 10 seconds, re-shows every 60 seconds.

### 5.3 Flush watch time on app stop

In `PlaybackScreen`'s `DisposableEffect`, ensure `PlayEventRecorder.endEvent()` is called on dispose (already done). In `ParentApprovedApp` or `MainActivity`, add `onStop()` that calls `PlayEventRecorder.flushCurrentEvent()` to persist current elapsed time to Room before Android kills the process. **(Security C3: force-kill loses in-memory state)**

### 5.4 Run all tests (GREEN)

`scripts/ui-test.sh` + `scripts/test-suite.sh` — all new tests pass.

**Commit:** `Integrate time limits into PlaybackScreen + HomeScreen`

---

## Phase 6: Dashboard — Screen Time UI

**Goal:** Parents can see and control time limits from their phone.

### 6.1 Write HTTP behavioral tests (RED)

Add to `scripts/test-suite.sh`:
- `test_get_time_limits_default` — GET /time-limits → assert no limits set, status "allowed"
- `test_put_daily_limit` — PUT /time-limits with monday=60 → GET → assert monday=60
- `test_post_lock` — POST /time-limits/lock → GET → assert manuallyLocked=true
- `test_post_bonus` — set limit + exhaust → POST /time-limits/bonus 15 → GET → assert remainingMin > 0

### 6.2 Add Screen Time section to dashboard HTML

In `index.html`, new section between Now Playing and Playlists:
- "Screen Time" header
- Today's usage bar (used / limit)
- Remaining time display
- Lock/Unlock button
- Bonus time buttons (visible when locked or low time)
- "Edit Limits" button → opens modal

### 6.3 Add edit modal to dashboard HTML

Start simple **(Dev Review: 7-day grid is scope creep for v0.7.0)**:
- Daily limit: single number input (applies to all days), with "No limit" toggle
- Bedtime: start/end time inputs (same every day), with "Off" toggle
- Save/Cancel buttons

Per-day-of-week grid deferred to v0.7.1.

### 6.4 Add dashboard JS

In `app.js`:
- `loadTimeLimits()` — GET /time-limits, render Screen Time section
- `saveTimeLimits(config)` — PUT /time-limits
- `toggleLock(locked)` — POST /time-limits/lock
- `grantBonusTime(minutes)` — POST /time-limits/bonus
- Call `loadTimeLimits()` in `loadDashboard()` and in the adaptive poll loop
- Update poll to reflect lock state (show "TV is locked" in now-playing area)

### 6.5 Add same to relay dashboard

Mirror the Screen Time section in `relay/assets/index.html` and `relay/assets/app.js`. The relay's generic WebSocket bridge forwards these API calls automatically — no relay code changes needed. **(Dev Review: relay proxies all paths generically)**

### 6.6 Run dashboard tests

Manual: open dashboard on phone, verify all controls work.
Behavioral: `scripts/test-suite.sh` HTTP tests from 6.1.

**Commit:** `Add Screen Time dashboard UI + relay support`

---

## Phase 7: Request More Time (P2)

**Goal:** Child can signal the parent from the lock screen.

### 7.1 Write tests (RED)

Unit tests:
- `requestMoreTime_setsFlag`
- `requestMoreTime_cooldown_rejectsDuplicate`
- `requestMoreTime_afterCooldown_allowsAgain`
- `requestMoreTime_persistsCooldownTimestamp` (survives force-kill)

Route tests:
- `postRequest_setsFlag_returns200`
- `postRequest_withinCooldown_returns429`
- `getTimeLimits_hasTimeRequest_true`

UI tests (`ui-test.sh`):
- `test_request_button_visible_on_lock_screen`
- `test_request_button_disabled_after_press`

### 7.2 Implement

- "Request More Time" button on LockScreen sends `POST /time-limits/request` to localhost:8080
- `TimeLimitManager` holds `hasTimeRequest` flag + `lastRequestTime` for cooldown, persisted in Room **(Security M2)**
- Dashboard JS polls flag, shows notification banner: "Your child is requesting more time. [+15 min] [+30 min]"
- Granting bonus clears the request flag

**Commit:** `Add "Request More Time" from lock screen (P2)`

---

## Phase 8: Polish + full regression

**Goal:** Everything works together. No regressions.

### 8.1 Full test sweep

| Suite | Command | Expected |
|-------|---------|----------|
| Unit tests | `./gradlew testDebugUnitTest` | All pass (187 existing + ~55 new) |
| Intent + HTTP | `scripts/test-suite.sh` | All pass (15 existing + ~8 new) |
| UI tests | `scripts/ui-test.sh` | All pass (34 existing + ~8 new, minus pre-existing flaky) |
| Instrumented | `./gradlew connectedDebugAndroidTest` | All pass (19 existing + 1 migration) |

### 8.2 Edge case manual testing

On emulator + Mi Box:
- Set limit to 1 minute → watch → verify lock at ~60s
- Set bedtime to now → verify immediate lock
- Grant bonus during bedtime → verify unlock
- Lock manually → verify stops playback mid-video
- Unlock manually → verify returns to home (not mid-video)
- Set limit → watch → pause for 5 min → resume → verify paused time not counted
- Playing video at midnight → verify timer resets, playback continues
- Multiple bonus grants → verify accumulation
- Bonus expires (play through it) → verify re-lock
- No config set → verify no limits enforced (backward compatible)
- Force-stop app during playback → relaunch → verify watch time was persisted **(Security C3)**
- Press Home during lock screen → relaunch → verify lock screen reappears **(Security C2)**

### 8.3 Dashboard manual testing

On phone browser (local + relay):
- Set daily limit → verify save + reload
- Set bedtime → verify save + reload
- Lock/unlock toggle → verify TV responds within 5 seconds
- Grant bonus → verify TV unlocks
- Verify Screen Time section updates in real time during playback

### 8.4 Version bump + release notes

- Bump to v0.7.0, versionCode 8
- Write `docs/RELEASE-v0.7.md` with haiku
- Write `docs/RETRO-v0.7.md`

**Commit:** `v0.7.0 "The Clock" — time controls for parents`

---

## Deferred to v0.7.1+

- **Per-day-of-week daily limits** (P1) — upgrade "same every day" to 7-day grid
- **Per-day-of-week bedtime** (P1) — upgrade single bedtime to per-day schedule
- **Per-playlist time limits** (P2) — separate table, `PLAYLIST_LIMIT` enum already defined
- **HTTPS / self-signed TLS** (Security H2) — cleartext HTTP on local network; documented as known limitation
- **CORS headers** (Security M5) — defense-in-depth, auth is primary gate
- **Session audit log** (Security L3) — track who changed what

---

## Test Count Estimate

| Category | Existing | New (v0.7) | Total |
|----------|----------|------------|-------|
| Unit (TimeLimitManager) | — | ~35 | 35 |
| Unit (TimeLimitDao / Fake) | — | ~7 | 7 |
| Unit (WatchTimeProvider) | — | ~3 | 3 |
| Unit (PinManager lockout persistence) | — | ~2 | 2 |
| Route (TimeLimitRoutes) | — | ~20 | 20 |
| Existing unit | 187 | 0 | 187 |
| Intent + HTTP (test-suite.sh) | 15 | ~8 | 23 |
| UI (ui-test.sh) | 34 | ~8 | 42 |
| Instrumented | 19 | ~1 | 20 |
| **Total** | **255** | **~84** | **~339** |

---

## File Inventory

New files:
- `tv/parentapproved/app/timelimits/TimeLimitManager.kt`
- `tv/parentapproved/app/timelimits/TimeLimitConfig.kt` (data models + sealed classes)
- `tv/parentapproved/app/timelimits/TimeLimitStore.kt` (interface)
- `tv/parentapproved/app/timelimits/WatchTimeProvider.kt` (interface)
- `tv/parentapproved/app/timelimits/RoomTimeLimitStore.kt`
- `tv/parentapproved/app/timelimits/RoomWatchTimeProvider.kt`
- `tv/parentapproved/app/data/cache/TimeLimitConfigEntity.kt`
- `tv/parentapproved/app/data/cache/TimeLimitDao.kt`
- `tv/parentapproved/app/server/TimeLimitRoutes.kt`
- `tv/parentapproved/app/ui/screens/LockScreen.kt`
- `test/.../timelimits/TimeLimitManagerTest.kt`
- `test/.../timelimits/TimeLimitDaoTest.kt`
- `test/.../server/TimeLimitRoutesTest.kt`
- `test/.../timelimits/WatchTimeProviderTest.kt`

Modified files:
- `data/cache/CacheDatabase.kt` — add entity + DAO + MIGRATION_3_4, version 4
- `server/ParentApprovedServer.kt` — register timeLimitRoutes
- `ServiceLocator.kt` — add timeLimitManager (optional in initForTest)
- `auth/PinManager.kt` — persist lockout counters to SharedPreferences
- `debug/DebugReceiver.kt` — gate on IS_DEBUG + 9 new intents
- `ui/screens/PlaybackScreen.kt` — pre-play check + separate 30s LaunchedEffect + warning overlay + onLocked callback + flushCurrentEvent
- `ui/screens/HomeScreen.kt` — synchronous lock check + onLocked callback
- `ui/navigation/AppNavigation.kt` — add lock route + onLocked wiring with popUpTo
- `data/events/PlayEventRecorder.kt` — add flushCurrentEvent() method
- `AndroidManifest.xml` — register new intent actions
- `assets/index.html` — Screen Time section + edit modal
- `assets/app.js` — time limit API calls + UI rendering
- `assets/style.css` — Screen Time section styles
- `relay/assets/index.html` — mirror Screen Time section
- `relay/assets/app.js` — mirror time limit JS
- `scripts/test-suite.sh` — new HTTP tests
- `scripts/ui-test.sh` — new lock screen + playback tests

---

## Dependencies & Risks

| Risk | Mitigation |
|------|------------|
| Room migration breaks existing data | Migration is additive (new table). Test in Phase 2.5. Version 3→4. |
| Lock screen bypass via Home button | Re-check `canPlay()` on every resume. Synchronous check before rendering HomeScreen. Test explicitly. **(Security C2)** |
| Lock screen bypass via Back button | Intercept in `onKeyEvent`. Test explicitly. |
| Force-kill loses current video elapsed | `flushCurrentEvent()` in `onStop()`. Worst case: 10s uncounted. Acceptable. **(Security C3)** |
| Debug intents in release builds | Gated on `BuildConfig.IS_DEBUG` in Phase 0. **(Security C1)** |
| Bonus + bedtime interaction | Bonus = active-playback minutes. 5 dedicated unit tests. Clarified in Phase 1 design notes. |
| PlaybackScreen complexity (450+ lines) | Time limit check in separate LaunchedEffect. Post-v0.7 refactor to ViewModel if needed. |
| System clock manipulation | Known limitation for v0.7. `sumDurationToday()` uses wall clock. Future: `elapsedRealtime()` for durations. |
| Relay forwarding of new routes | Relay bridges all HTTP generically. No TV-side relay code changes needed. |
| ServiceLocator.initForTest() breaking existing tests | Optional parameter with default no-op. |
| Midnight rollover edge cases | 3 automated unit tests in Phase 1.2. |
