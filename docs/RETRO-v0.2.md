# KidsWatch v0.2 Retrospective

**Date:** February 17, 2026
**Scope:** From feasibility test through first real-hardware deployment

---

## Timeline

1. **Feasibility phase** — Explored 4 approaches: AccountManager tokens, WebView sign-in, YouTube embeds, NewPipeExtractor. Only NewPipe worked without restrictions.
2. **v0.1 prototype** — Firebase-based pairing, ExoPlayer playback. Worked in emulator but Firebase added unnecessary complexity for a local-use app.
3. **v0.2 architecture** — Rewrote to local-first: embedded Ktor server, Room DB, PIN auth. Full TDD with 10 implementation phases.
4. **v0.2 testing** — 138 automated tests, automated UI test suite, CI pipeline script.
5. **v0.2 deploy** — First install on Xiaomi Mi Box 4. Everything worked on first try.

---

## What Went Well

### TDD Paid Off
Writing tests first for every phase caught real bugs before they became problems. The Ktor route tests found auth edge cases. The PIN manager tests validated rate limiting logic. When we deployed to real hardware, confidence was high — and justified.

### The Architecture Decision
Dropping Firebase for a local embedded server was the right call. No cloud dependency, no sign-in friction, no latency. The phone connects directly to the TV over WiFi. Simple, fast, private.

### First Deploy Success
Every feature worked on real hardware on the first attempt:
- Playlist loading with thumbnails
- Video playback (2-5s cold, 1-2s warm)
- Phone dashboard with QR pairing
- Add/remove playlists from phone
- Activity tracking
- Navigation (Back button, Settings, etc.)

### Automated Test Infrastructure
The combination of unit tests (Gradle), instrumented tests (Android), ADB intent tests, and UI tests (uiautomator) gives us a solid regression net. The `ci-run.sh` script runs everything end-to-end.

### Debug Intent System
The 16 debug intents made testing dramatically easier. Every piece of app state is inspectable and controllable via `adb shell am broadcast`. This will pay dividends in every future version.

---

## What Didn't Go Well

### The `HOME=3` Bug
A shell variable named `HOME=3` (for the Android home key code) overwrote the `$HOME` environment variable, causing ADB to crash with `Cannot mkdir '3/.android'`. This took real debugging time for a trivial naming collision. **Lesson:** Never use `HOME`, `PATH`, `USER`, or other reserved names for script variables.

### Fragile D-pad Navigation in UI Tests
The initial UI test approach used D-pad key presses to navigate to buttons. This was completely unreliable — the focus order depended on layout, and any UI change broke navigation. **Fix:** Switched to tap-based navigation using uiautomator bounds parsing. More code, but deterministic.

### PIN Changing After Force-Stop
Force-stopping the app in Test 3 (to reload playlists) caused the PIN to regenerate, which broke the HTTP auth test later. **Fix:** Re-fetch PIN via `DEBUG_GET_PIN` intent before HTTP tests. **Lesson:** When tests restart the app, re-acquire any ephemeral state.

### Case-Insensitive grep False Positive
`grep -qi "Connect Your Phone"` matched the home screen's "Connect your phone to add YouTube playlists" text, causing a negative assertion to fail. **Fix:** Use text unique to the target screen (e.g., "Scan the QR") for negative assertions. **Lesson:** Compose NavHost keeps both screens in the view hierarchy during transitions — be specific with UI assertions.

### ADB Command Approval Fatigue
During development, every `adb` command required manual approval. Over the course of a session with dozens of ADB operations (install, shell commands, logcat, forward, input), this created significant friction. **Action items:**
- Consider adding ADB commands to an auto-approve allowlist for the KidsWatch project
- The commands are low-risk (install to emulator/device, read logcat, send intents, UI dumps)
- Specific patterns to allow: `adb -s ... install`, `adb shell am`, `adb shell input`, `adb logcat`, `adb shell uiautomator`, `adb forward`, `adb pull`
- This would dramatically speed up the develop-test-fix cycle

### Overscan Not Accounted For
The TV app looks slightly cut off on the Panasonic TV connected to the Mi Box. Standard Android TV overscan (5% safe area) wasn't implemented. Should have been in v0.2. **Fix:** Add to v0.3 — apply 48dp padding or use `Modifier.systemBarsPadding()`.

---

## Learnings for Future Versions

1. **Test on real hardware earlier.** The emulator is great for development but can't catch overscan, input lag, or remote control UX issues.

2. **Ephemeral state in tests is tricky.** Any test that restarts the app needs to re-acquire PINs, tokens, and session state. Design tests to be self-contained.

3. **uiautomator dump + tap > D-pad navigation** for automated UI testing. Coordinates from bounds are deterministic; focus order is not.

4. **Shell script variable naming matters.** Reserved environment variable names will silently break tools that depend on them.

5. **The haiku tradition.** Every release note gets a haiku. It forces a moment of reflection and celebration before moving on to the next task.

6. **Local-first is underrated.** No Firebase, no API keys, no cloud functions, no billing alerts. The TV runs a web server. The phone connects to it. That's it.

7. **NewPipe extraction latency is acceptable.** 2-5s on first play, 1-2s after that. For a kid picking a video, this is fine. No need to pre-extract.

8. **ADB intent testing is powerful.** Being able to `adb shell am broadcast` to inject playlists, reset state, simulate offline mode, and dump state makes the app fully automatable without any UI interaction.

---

## v0.3 Priorities (from this retro)

| Priority | Item | Why |
|----------|------|-----|
| P0 | Playback controls (play/pause, next/prev) | D-pad does nothing during playback on real TV |
| P0 | App launcher icon/banner | Can't find app easily on Android TV home |
| P1 | Overscan safe area padding | Content cut off on edges |
| P1 | Playlist display names | Shows raw YouTube ID, not human name |
| P2 | ADB auto-approve for dev workflow | Reduce approval fatigue in Claude Code sessions |

---

## Final Note

From "can we even play a YouTube video on Android TV without the YouTube app?" to a working app on a real TV in the living room, with 138 automated tests and a phone dashboard. The answer is yes. And it only took NewPipe, ExoPlayer, Ktor, Room, a QR code, and a 6-digit PIN.
