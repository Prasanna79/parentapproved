# KidsWatch — Claude Code Init

## What This Is
Android TV app for kid-safe YouTube viewing. Parents manage playlists from their phone; kids watch on the TV. No ads, no algorithm, no YouTube chrome.

## Architecture (v0.3)
- **TV app** (`tv-app/`): Kotlin, Jetpack Compose, ExoPlayer, Ktor embedded server
- **Phone dashboard**: HTML/CSS/JS served from tv-app assets via Ktor on port 8080
- **Video extraction**: NewPipeExtractor (no API key, no sign-in)
- **Storage**: Room DB (playlists, video cache, play events)
- **Auth**: 6-digit PIN + session tokens (local, no cloud, localStorage persistence)
- **Playback control**: PlaybackCommandBus (SharedFlow), HTTP API + D-pad

## Directory Structure
```
KidsWatch/
├── tv-app/                    # Android TV app (the product)
│   ├── app/src/main/java/com/kidswatch/tv/
│   │   ├── auth/              # PinManager, SessionManager
│   │   ├── data/              # Room DB, PlaylistRepository, models
│   │   ├── debug/             # DebugReceiver (16 ADB intents)
│   │   ├── playback/          # StreamSelector, PlaybackCommandBus, DpadKeyHandler
│   │   ├── server/            # Ktor routes (auth, playlists, stats, playback, dashboard)
│   │   ├── ui/screens/        # HomeScreen, PlaybackScreen, ConnectScreen, SettingsScreen
│   │   ├── ui/navigation/     # AppNavigation, Routes
│   │   ├── ui/theme/          # Colors, Theme
│   │   └── util/              # QrCodeGenerator, NetworkUtils, PlaylistUrlParser
│   ├── app/src/main/assets/   # Parent dashboard (index.html, app.js, style.css)
│   ├── app/src/test/          # 105 unit tests
│   ├── app/src/androidTest/   # 19 instrumented tests
│   └── scripts/               # ci-run.sh, test-suite.sh, ui-test.sh
├── android-tv-test/           # Feasibility test app (reference only)
├── docs/                      # Specs, reviews, release notes, images
└── CLAUDE.md                  # This file
```

## Build & Run
```bash
# Set env (or export in shell profile)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$PATH:$ANDROID_HOME/platform-tools"
export ADB_RUN="/opt/homebrew/share/android-commandlinetools/platform-tools/adb"

# Build
cd tv-app && ./gradlew assembleDebug

# Install to device use the right adb path here
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/opt/homebrew/share/android-commandlinetools/platform-tools/adb shell am start -n com.kidswatch.tv/.MainActivity

# Run unit tests
./gradlew testDebugUnitTest

# Run full CI (unit + instrumented + intent + UI)
bash scripts/ci-run.sh
```

## Devices
- **Emulator**: TV_API34, arm64-v8a, `-no-audio`, `swiftshader_indirect` GPU
- **Real device**: Xiaomi Mi Box 4 (MIBOX4), Android 9 (API 28), `adb connect 192.168.0.101:5555`

## Key Conventions
- **TDD**: Write tests first, verify they fail, implement, verify they pass
- **Debug intents**: All state inspectable/controllable via `adb shell am broadcast -a com.kidswatch.tv.DEBUG_*`
- **Logcat tag**: `KidsWatch-Intent` for all debug intent JSON output
- **DI**: ServiceLocator (lightweight, no Hilt). Use `initForTest()` in tests
- **Release notes**: Every version gets a haiku
- **Shell scripts**: Never use reserved env var names (HOME, PATH, USER)
- **UI tests**: Use uiautomator tap (bounds parsing), not D-pad navigation

## Test Summary (v0.3)
| Suite | Count | Runner |
|-------|-------|--------|
| Unit tests | 105 | `./gradlew testDebugUnitTest` |
| Instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| Intent + HTTP | 15 | `scripts/test-suite.sh` |
| UI tests | 34 | `scripts/ui-test.sh` |
| **Total** | **173** | `scripts/ci-run.sh` |

## ADB
- Use `/adb` slash command or full path: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
- Mi Box: `-s 192.168.0.101:5555` | Emulator: `-s emulator-5554`

## Moldable Development — Friction-Driven
- **Friction log**: `docs/friction-log.md` — updated during every bug fix or investigation
- **Before reading code** for a bug/question, note what question you're answering
- **After resolving**, append an entry: question, files read, trace length, root cause, what would have helped
- **3-strike rule**: when a friction pattern appears 3 times, build the domain object/view for it
- **Reference designs**: `v031-MOLDABLE-DEV-SPEC.md` has pre-designed domain objects (ResolutionAttempt, PlaybackSession, ParentAction, WatchableContent) — pull from this menu when friction justifies it
- **Don't pre-build** — let real debugging pain drive what gets built

## What NOT to Do
- Don't add Firebase or any cloud dependencies — this is local-first by design
- Don't use YouTube embed URLs (Error 152 in WebView)
- Don't use WebView for Google sign-in (blocked by Google)
- Don't use `HOME` as a variable name in shell scripts
- Don't map D-pad Left/Right to playlist skip — it breaks ExoPlayer's controller navigation
