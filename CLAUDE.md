# ParentApproved.tv — Claude Code Init

## What This Is
Android TV app for kid-safe YouTube viewing. Parents manage content sources from their phone; kids watch on the TV. No ads, no algorithm, no YouTube chrome.

**Website:** [parentapproved.tv](https://parentapproved.tv)

## Architecture (v0.9)
- **TV app** (`tv-app/`): Kotlin, Jetpack Compose, ExoPlayer, Ktor embedded server
- **Phone dashboard**: HTML/CSS/JS served from tv-app assets via Ktor on port 8080
- **Relay** (`relay/`): Cloudflare Workers + Durable Objects for remote access
- **Video extraction**: NewPipeExtractor (no API key, no sign-in)
- **Storage**: Room DB (content sources, video cache, play events)
- **Auth**: 6-digit PIN + session tokens (local, no cloud, localStorage persistence)
- **Playback control**: PlaybackCommandBus (SharedFlow), HTTP API + D-pad

## Directory Structure
```
ParentApproved/
├── tv-app/                    # Android TV app (the product)
│   ├── app/src/main/java/tv/parentapproved/app/
│   │   ├── auth/              # PinManager, SessionManager
│   │   ├── data/              # Room DB, ContentSourceRepository, models
│   │   ├── debug/             # DebugReceiver (18 ADB intents)
│   │   ├── playback/          # StreamSelector, PlaybackCommandBus, DpadKeyHandler
│   │   ├── relay/             # RelayConnector, RelayConfig, RelayProtocol
│   │   ├── server/            # Ktor routes (auth, playlists, stats, playback, dashboard, status)
│   │   ├── ui/screens/        # HomeScreen, PlaybackScreen, ConnectScreen, SettingsScreen
│   │   ├── ui/navigation/     # AppNavigation, Routes
│   │   ├── ui/theme/          # Colors, Theme
│   │   └── util/              # QrCodeGenerator, NetworkUtils, ContentSourceParser
│   ├── app/src/main/assets/   # Parent dashboard (index.html, app.js, style.css, favicon.svg)
│   ├── app/src/test/          # Unit tests
│   └── app/src/androidTest/   # Instrumented tests
├── relay/                     # Cloudflare Workers relay
│   ├── src/                   # TypeScript source
│   ├── assets/                # Dashboard (relay copy)
│   └── test/                  # Vitest tests
├── marketing/landing-page/    # parentapproved.tv website
├── docs/                      # Specs, release notes, retros, friction log
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
/opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run unit tests
./gradlew testDebugUnitTest

# Run relay tests
cd relay && npx vitest run

# Deploy relay
cd relay && npx wrangler deploy

# Deploy marketing site
cd marketing/landing-page && npx wrangler pages deploy . --project-name parentapproved-tv
```

## Devices
- **Emulator**: TV_API34, arm64-v8a, `-no-audio`, `swiftshader_indirect` GPU
- **Real device**: Xiaomi Mi Box 4 (MIBOX4), Android 9 (API 28), `adb connect 192.168.0.101:5555`

## Key Conventions
- **TDD**: Write tests first, verify they fail, implement, verify they pass
- **Debug intents**: All state inspectable/controllable via `adb shell am broadcast -a tv.parentapproved.app.DEBUG_*`
- **Logcat tag**: `KidsWatch-Intent` for all debug intent JSON output
- **DI**: ServiceLocator (lightweight, no Hilt). Use `initForTest()` in tests
- **Release notes**: Every version gets a haiku
- **Shell scripts**: Never use reserved env var names (HOME, PATH, USER)
- **UI tests**: Use uiautomator tap (bounds parsing), not D-pad navigation
- **Dashboard sync**: Local (`tv-app/app/src/main/assets/`) and relay (`relay/assets/`) copies must be updated together
- **Package**: `tv.parentapproved.app` (renamed from `com.kidswatch.tv` in v0.4.1)

## Test Summary (v0.9.2)
| Suite | Count | Runner |
|-------|-------|--------|
| TV unit tests | 302 | `./gradlew testDebugUnitTest` |
| TV instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| Relay tests | 220 | `cd relay && npx vitest run` |
| Landing page tests | 10 | `cd marketing/landing-page && npx vitest run` |
| Digest worker tests | 9 | `cd marketing/notify-digest && npx vitest run` |
| Playwright deploy smoke | 11 | `bash tv-app/scripts/ci-run.sh playwright-smoke` |
| **Total** | **571** | |

**Quick runner**: `bash tv-app/scripts/ci-run.sh [suite]` — runs everything by default. Valid suites: `unit`, `instrumented`, `relay`, `landing`, `intent`, `ui`, `smoke`, `playwright-smoke`.

## ADB
- Use `/adb` slash command or full path: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
- Mi Box: `-s 192.168.0.101:5555` | Emulator: `-s emulator-5554`

## Deploy Verification (Required)

Every release must pass all verification layers before it's considered deployed:

1. **Unit tests**: `cd tv-app && ./gradlew testDebugUnitTest`
2. **Instrumented tests**: `cd tv-app && ./gradlew connectedDebugAndroidTest`
3. **Relay tests** (includes route-alignment): `cd relay && npx vitest run`
4. **Playwright browser tests**: `cd relay && npx playwright test`
5. **Emulator deploy smoke**: `bash tv-app/scripts/deploy-smoke.sh` (app must be running)
6. **Playwright deploy smoke**: `bash tv-app/scripts/ci-run.sh playwright-smoke` (works on release APK — no debug intents needed)
7. **Relay deploy smoke**: `bash relay/test/deploy-smoke.sh <RELAY_URL>`

**When adding dashboard features**: parity test + Playwright + both deploy smokes must pass
**When adding API routes**: update `relay/src/allowlist.ts` + route-alignment test must pass
**No deploy is complete until smoke tests pass on the running artifact**

## Moldable Development — Friction-Driven
- **Friction log**: `docs/friction-log.md` — updated during every bug fix or investigation
- **Before reading code** for a bug/question, note what question you're answering
- **After resolving**, append an entry: question, files read, trace length, root cause, what would have helped
- **3-strike rule**: when a friction pattern appears 3 times, build the domain object/view for it
- **Reference designs**: `v-future-MOLDABLE-DEV-SPEC.md` has pre-designed domain objects (ResolutionAttempt, PlaybackSession, ParentAction, WatchableContent) — pull from this menu when friction justifies it
- **Don't pre-build** — let real debugging pain drive what gets built

## Distribution (v0.9+)
- **Sideload only** (no Play Store). F-Droid if demand warrants it
- **License**: open source forever, charityware (mettavipassana.org)
- **APK hosting**: GitHub Releases (stable URL: `/releases/latest/download/ParentApproved.apk`)
- **Update check**: `version.json` on parentapproved.tv, app checks on startup + every 24h
- **Signing key**: release keystore in 1Password + GitHub Actions secret + cold USB backup. NEVER commit the keystore or passwords. Local builds read from `local.properties`, CI reads from env vars
- **versionCode**: MUST increment on every APK distributed to anyone. Android refuses to install a lower versionCode over a higher one
- **Release build**: `cd tv-app && ./gradlew assembleRelease` (requires signing config)

## CI/CD
- **Repo**: `https://github.com/Prasanna79/parentapproved`
- **GitHub Actions**: `build.yml` (every push/PR), `release.yml` (manual trigger for releases)
- **`build.yml` pipeline**: unit tests → debug build → Firebase Test Lab (19 instrumented tests) → relay tests → upload debug APK artifact
- **`release.yml` pipeline**: unit tests → relay tests → sign APK → Firebase Test Lab → GitHub Release → auto-update `version.json` → deploy landing page
- **Firebase Test Lab**: uses **debug APK** (not release) — DebugReceiver needs `IS_DEBUG=true`. Custom results bucket (`$PROJECT_ID-test-results`). Service account needs `roles/editor`. Cost: ~$0.08/run
- **Firebase Test Lab setup**: enable both `testing.googleapis.com` and `toolresults.googleapis.com`. Use `--project` and `--results-bucket` flags explicitly
- **Dependabot**: `.github/dependabot.yml` monitors Gradle + npm dependencies weekly
- **Anti-cloud clarification**: the app is local-first (no cloud accounts required from users). Dev infrastructure (GitHub, Cloudflare, Firebase Test Lab) is fair game

## What NOT to Do
- Don't add cloud dependencies **to the app** — parents and kids should never need a cloud account
- Don't use YouTube embed URLs (Error 152 in WebView)
- Don't use WebView for Google sign-in (blocked by Google)
- Don't use `HOME` as a variable name in shell scripts
- Don't map D-pad Left/Right to playlist skip — it breaks ExoPlayer's controller navigation
- Don't commit the release keystore or its passwords
- Don't ship an APK without incrementing versionCode
- Don't bump `PROTOCOL_VERSION` without relay supporting both current and previous version for 30 days
- Don't use release APK for Firebase Test Lab — use debug APK (DebugReceiver checks `IS_DEBUG`)
- Don't gitignore `package-lock.json` — CI needs it for `npm ci` and cache
- Don't claim "known bug" without a documentation link — provide evidence or say "not sure"
- Don't do bulk uiautomator dumps while expecting Ktor to stay responsive — multiple dumps kill the embedded server. Restart app after dumps if you need HTTP access
- Don't create new Playwright pages per test when talking to Ktor via adb forward — share a single page. New TCP connections get dropped
- Don't use `page.request.get()` against Ktor via adb forward — use in-page `fetch()` to reuse the existing connection
