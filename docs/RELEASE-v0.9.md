# ParentApproved.tv v0.9 "The Door" — Release Notes

**Date:** February 22, 2026
**Milestone:** The app leaves the dev machine. Friends can download, install, and report back.

```
the door swings open —
friends gather, install, and play,
feedback lights the way.
```

---

## What's New

v0.9 is the **friends-and-family distribution release**. No new viewing features. The goal: make the app installable, updatable, observable, and open source.

### Release Signing & Build Pipeline
- Release APK is now properly signed with a permanent keystore (1Password + GitHub Actions secret + cold USB backup)
- `./gradlew assembleRelease` produces a signed, installable APK
- `build.gradle.kts` signing config reads from `local.properties` for local builds, environment variables for CI
- R8/ProGuard deferred to v0.9.1 — Ktor, kotlinx.serialization, NewPipeExtractor, and Room all need careful keep rules
- versionCode bumped to 12, versionName to 0.9.0

### Download Page & Sideload Guide
- Landing page "coming soon" email form replaced with an actual download button
- Download points to GitHub Releases stable URL: `/releases/latest/download/ParentApproved.apk`
- Four sideload methods documented: Downloader app (recommended), Send Files to TV, USB drive, ADB
- "Install unknown apps" instructions for Google TV, Mi Box, and Fire TV
- `/apk` redirect on parentapproved.tv for easy sharing
- Version info fetched from `version.json` and displayed on the page

### Version Visibility
- ConnectScreen shows version in both debug and release builds (removed `IS_DEBUG` gate)
- Dashboard footer displays version from `/api/status`
- `protocolVersion` added to status response
- Dashboard "Report a Problem" mailto link with pre-filled subject and version

### Crash Visibility
- `Thread.setDefaultUncaughtExceptionHandler` in `ParentApprovedApp.onCreate()`
- Writes stack trace, app version, device model, Android version, timestamp to internal storage
- Keeps last 5 crashes, caps total size at 100KB
- Delegates to previous handler after writing (no zombie processes)
- `GET /api/crash-log` (authenticated) returns crash log contents
- Dashboard shows crash info with copy-to-clipboard for feedback emails

### Update Checker
- On startup and every 24 hours, fetches `version.json` from parentapproved.tv
- Compares `latestCode` to `BuildConfig.VERSION_CODE`
- Shows subtle banner on ConnectScreen: "Update available (vX.Y.Z)"
- Dashboard shows update badge next to version in footer
- No auto-download, no in-app install — just awareness

### Cleartext Traffic Lockdown
- Replaced `android:usesCleartextTraffic="true"` with `network_security_config.xml`
- Cleartext restricted to localhost and RFC 1918 private ranges only
- Documents the intent: cleartext is only for the embedded Ktor server

### CI/CD Pipeline (GitHub Actions)
- **`build.yml`**: runs on every push and PR — unit tests, debug build, Firebase Test Lab instrumented tests, relay tests
- **`release.yml`**: manual trigger — unit tests, relay tests, sign APK, Firebase Test Lab, create GitHub Release, auto-update `version.json`, deploy landing page
- Firebase Test Lab runs 19 instrumented tests on cloud devices (MediumPhone.arm, API 34)
- CI-built APK saved as artifact for download and verification
- Custom results bucket (`$PROJECT_ID-test-results`) for Test Lab output

### Dependabot
- `.github/dependabot.yml` monitors four ecosystems: Gradle (`tv-app/`), npm (`relay/`, `marketing/landing-page/`, `marketing/notify-digest/`)
- Grouped PRs for related libraries (media3, ktor, compose, room)
- Weekly schedule, 5 open PRs per ecosystem
- CI runs automatically on Dependabot PRs — green means safe to merge

### Open Source
- AGPL-3.0 license added
- Charityware note: "If it's useful, consider donating to mettavipassana.org"
- Git history audited for secrets before making repo public
- GitHub URL placeholders updated to `https://github.com/Prasanna79/parentapproved`

---

## Files Changed (34 files)

| Category | Files |
|----------|-------|
| **CI/CD** | `.github/workflows/build.yml`, `.github/workflows/release.yml`, `.github/dependabot.yml` |
| **Signing & build** | `tv-app/app/build.gradle.kts` |
| **TV app source** | `CrashHandler.kt` (new), `UpdateChecker.kt` (new), `CrashLogRoutes.kt` (new), `ParentApprovedApp.kt`, `ServiceLocator.kt`, `StatusRoutes.kt`, `ParentApprovedServer.kt`, `ConnectScreen.kt` |
| **TV config** | `AndroidManifest.xml`, `network_security_config.xml` (new) |
| **TV dashboard** | `app.js`, `index.html`, `style.css` |
| **TV tests** | `CrashHandlerTest.kt` (new), `UpdateCheckerTest.kt` (new) |
| **Relay** | `relay/src/allowlist.ts`, `relay/test/route-alignment.test.ts` |
| **Landing page** | `index.html`, `main.js`, `style.css`, `_redirects` (new), `version.json` (new) |
| **License** | `LICENSE` (new, AGPL-3.0) |
| **Scripts** | `tv-app/scripts/ci-run.sh` (rewritten) |
| **Config** | `.gitignore` (removed package-lock.json ignore) |
| **Lock files** | `relay/package-lock.json`, `marketing/landing-page/package-lock.json`, `marketing/notify-digest/package-lock.json` |
| **Docs** | `CLAUDE.md`, `v0.9-THE-DOOR-SPEC.md` |

---

## Test Coverage

| Suite | v0.8 | v0.9 | Delta |
|-------|------|------|-------|
| TV unit tests | 277 | 302 | +25 |
| TV instrumented | 19 | 19 | 0 |
| Relay vitest | 218 | 220 | +2 |
| Playwright browser | 27 | 27 | 0 |
| Landing page | 10 | 10 | 0 |
| Digest worker | 9 | 9 | 0 |
| **Total** | **560** | **587** | **+27** |

Plus script-based smoke tests: 30 (emulator) + 25 (relay).
Plus Firebase Test Lab: 19 instrumented tests on cloud devices.

---

## Deployment Order

1. **Relay** (`cd relay && npx wrangler deploy`) — additive allowlist change for `/api/crash-log`
2. **Landing page** (`cd marketing/landing-page && npx wrangler pages deploy . --project-name parentapproved-tv`) — download page replaces email form
3. **TV APK** — release via `release.yml` or manual `./gradlew assembleRelease`
