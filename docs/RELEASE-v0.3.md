# KidsWatch v0.3 "The Remote" — Release Notes

**Date:** February 17, 2026
**Milestone:** Kids can control playback. Parents can control it too.

---

## The living room has a remote now

v0.2 played videos. v0.3 lets you actually control them. The kid picks up the remote: center button pauses, center again resumes, left and right seek through the video. The parent picks up their phone: sees what's playing, how far in, and can stop it or skip to the next video. Two remotes, one TV, no arguments.

```
Center button pressed—
the cartoon holds its breath, waits,
then plays on again.
```

---

## What's New

### Playback Controls (D-pad)
- **Center** — pause / resume
- **Left / Right** — seek within the video (ExoPlayer controller)
- **Back** — stop and return to home screen
- Media hardware keys (play/pause, next, prev) also work for Bluetooth remotes

### Parent Dashboard: Now Playing Card
- Live view of what's currently playing: video title, playlist name, progress bar
- Elapsed time and total duration, updated every 5 seconds while playing
- **Stop**, **Pause/Play**, and **Next** buttons — control playback from your phone
- Adaptive polling: 5s refresh while playing, 30s when idle

### Session Persistence
- Dashboard stays logged in across page refreshes (token saved in browser)
- No more re-entering the PIN every time you reload
- Expired or invalidated sessions gracefully fall back to PIN screen

### Reset PIN Clears Sessions
- Resetting the PIN now invalidates all existing sessions
- No more hitting the 5-session limit after a PIN reset
- Session count visible in Settings screen, updates immediately

### Playlist Display Names
- Playlists show their actual YouTube title instead of raw playlist IDs
- Titles extracted via NewPipeExtractor and cached in the database
- Dashboard and home screen both show human-readable names

### Overscan Safe Padding
- 48dp padding on Home, Connect, and Settings screens
- Playback stays full-bleed (video fills the entire screen)
- No more content cut off at the edges on real TVs

### App Launcher Banner
- KidsWatch now has a banner in the Android TV launcher
- Placeholder graphic (will be replaced with proper branding)

### Playback Control HTTP API
- `POST /playback/stop` — stop playback
- `POST /playback/skip` — skip to next video
- `POST /playback/pause` — toggle pause/resume
- All endpoints require authentication

### Developer Experience
- `/adb` slash command with baked-in device paths and all 16 debug intents
- Consolidated ADB permissions — single wildcard, no more approval fatigue
- Settings screen shows PIN and active session count with live updates

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| Unit tests | 105 | `./gradlew testDebugUnitTest` |
| Instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| Intent + HTTP | 15 | `scripts/test-suite.sh` |
| UI tests | 34 | `scripts/ui-test.sh` |
| **Total** | **173** | `scripts/ci-run.sh` |

New test files:
- `PlaybackCommandBusTest.kt` (5 tests)
- `DpadKeyHandlerTest.kt` (7 tests)
- `PlaybackRoutesTest.kt` (7 tests)
- `PlayEventRecorderTest.kt` (8 tests)
- `PlaylistRepositoryTest.kt` (4 tests)
- `SessionManagerTest.kt` (+1 test for reset-clears-sessions)
- `StatusRoutesTest.kt` (+3 tests for enriched now-playing)

---

## Known Issues

- **ExoPlayer Next/Prev buttons grayed out** — ForwardingPlayer `hasNextMediaItem()` override not respected by PlayerView's controller UI. Playlist navigation works via dashboard or auto-advance at end of video. Fix planned for v0.4.
- **Banner is placeholder** — Red circle on dark background. Needs proper branding artwork.

---

## Verified Hardware
- **Emulator:** TV_API34 (Android 14, arm64)
- **Real device:** Xiaomi Mi Box 4 (MIBOX4, Android 9, API 28) on Panasonic TV
