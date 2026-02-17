# KidsWatch — Claude Code Init

## What This Is
Android TV app for kid-safe YouTube viewing. Parents manage playlists from their phone; kids watch on the TV. No ads, no algorithm, no YouTube chrome.

## Architecture (v0.2)
- **TV app** (`tv-app/`): Kotlin, Jetpack Compose, ExoPlayer, Ktor embedded server
- **Phone dashboard**: HTML/CSS/JS served from tv-app assets via Ktor on port 8080
- **Video extraction**: NewPipeExtractor (no API key, no sign-in)
- **Storage**: Room DB (playlists, video cache, play events)
- **Auth**: 6-digit PIN + session tokens (local, no cloud)

## Directory Structure
```
KidsWatch/
├── tv-app/                    # Android TV app (the product)
│   ├── app/src/main/java/com/kidswatch/tv/
│   │   ├── auth/              # PinManager, SessionManager
│   │   ├── data/              # Room DB, PlaylistRepository, models
│   │   ├── debug/             # DebugReceiver (16 ADB intents)
│   │   ├── playback/          # StreamSelector
│   │   ├── server/            # Ktor routes (auth, playlists, stats, dashboard)
│   │   ├── ui/screens/        # HomeScreen, PlaybackScreen, ConnectScreen, SettingsScreen
│   │   ├── ui/navigation/     # AppNavigation, Routes
│   │   ├── ui/theme/          # Colors, Theme
│   │   └── util/              # QrCodeGenerator, NetworkUtils, PlaylistUrlParser
│   ├── app/src/main/assets/   # Parent dashboard (index.html, app.js, style.css)
│   ├── app/src/test/          # 70 unit tests
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

# Build
cd tv-app && ./gradlew assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kidswatch.tv/.MainActivity

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

## Test Summary (v0.2)
| Suite | Count | Runner |
|-------|-------|--------|
| Unit tests | 70 | `./gradlew testDebugUnitTest` |
| Instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| Intent + HTTP | 15 | `scripts/test-suite.sh` |
| UI tests | 34 | `scripts/ui-test.sh` |
| **Total** | **138** | `scripts/ci-run.sh` |

## What NOT to Do
- Don't add Firebase or any cloud dependencies — this is local-first by design
- Don't use YouTube embed URLs (Error 152 in WebView)
- Don't use WebView for Google sign-in (blocked by Google)
- Don't use `HOME` as a variable name in shell scripts
