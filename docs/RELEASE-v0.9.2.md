# ParentApproved.tv v0.9.2 — Release Notes

**Date:** February 22, 2026
**Milestone:** Playwright deploy smoke tests — verify the real APK works end-to-end.

```
screen speaks in code —
uiautomator reads the PIN,
release builds, tested.
```

---

## What's New

v0.9.2 adds a **Playwright-based deploy smoke test** that works on both debug and release APKs. The previous `deploy-smoke.sh` relied on debug broadcast intents (`DebugReceiver`), which are disabled in release builds. The new test extracts the PIN directly from the TV screen via `adb shell uiautomator dump`.

### Playwright Deploy Smoke (11 tests)

**Local dashboard (7 tests)**
- Auth screen visible at localhost:8080
- PIN auth → dashboard visible
- All dashboard sections present
- Version displayed in footer
- Security headers present (CSP, X-Frame-Options, X-Content-Type-Options)
- API endpoints return data (playlists, time-limits, stats)
- No uncaught JavaScript exceptions

**Relay dashboard (4 tests, skipped if relay not connected)**
- Dashboard loads at relay URL
- PIN auth → dashboard visible
- All sections present
- Per-TV localStorage key exists

### Key Design Decisions

- **Shared page pattern**: All tests share a single browser page — Ktor CIO via adb forward can't handle rapid TCP connection cycling
- **In-page fetch**: API tests use `page.evaluate(() => fetch(...))` instead of `page.request.get()` to reuse the existing connection
- **uiautomator recovery**: beforeAll extracts tvId first (expendable), restarts the app (uiautomator kills Ktor), then extracts PIN from the fresh app

---

## Files Changed (7 files)

| Category | Files |
|----------|-------|
| **New: smoke tests** | `tv-app/scripts/package.json`, `tv-app/scripts/package-lock.json`, `tv-app/scripts/adb-helpers.ts`, `tv-app/scripts/playwright-smoke.config.ts`, `tv-app/scripts/playwright-smoke.spec.ts` |
| **CI integration** | `tv-app/scripts/ci-run.sh` |
| **Config** | `.gitignore`, `tv-app/app/build.gradle.kts` (versionCode 14, versionName 0.9.2) |

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| TV unit tests | 302 | `./gradlew testDebugUnitTest` |
| TV instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| Relay tests | 220 | `cd relay && npx vitest run` |
| Playwright deploy smoke | 11 | `cd tv-app/scripts && npx playwright test` |
| Landing page tests | 10 | `cd marketing/landing-page && npx vitest run` |
| Digest worker tests | 9 | `cd marketing/notify-digest && npx vitest run` |
| **Total** | **571** | |

---

## Deployment

No app or relay code changes — this is a test infrastructure addition only. Run the new smoke test against a running APK:

```bash
cd tv-app/scripts && npm install && npx playwright install chromium
npx playwright test --config playwright-smoke.config.ts
```

Or via ci-run.sh:

```bash
bash tv-app/scripts/ci-run.sh playwright-smoke
```
