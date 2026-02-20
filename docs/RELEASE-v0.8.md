# ParentApproved.tv v0.8 "The Audit" — Release Notes

**Date:** February 20, 2026
**Milestone:** Four independent reviews, sixteen fixes, zero new features. Ship it, cleaned and true.

```
four eyes read the code —
leaked sessions, missing routes found,
ship it, cleaned and true.
```

---

## What's New

v0.8 is a **quality and correctness release**. Four independent reviews (architecture, security, test coverage, dev manager) produced 16 findings. All Tier 1 and Tier 2 items are resolved. No new features, no UI changes.

### Tier 1: Release Blockers Fixed

#### 1. Double Session Leak on PIN Auth
Every successful PIN auth was creating two sessions — one from `PinManager.onPinValidated`, another from `AuthRoutes`. Session A leaked (never returned to anyone). After ~10 logins, half the session pool was wasted. **Fix:** `AuthRoutes` now uses the token already in `PinResult.Success.token`.

#### 2. Time-Limit Routes Missing from Relay Allowlist
The v0.7.0 headline feature ("The Clock") was broken over the relay. No `/api/time-limits` paths in `allowlist.ts`, and `PUT` wasn't in `validMethods`. Parents couldn't manage time limits remotely. **Fix:** Added 4 route entries + PUT to valid methods. Relay body-size check now covers PUT.

#### 3. ConcurrentHashMap in SessionManager
`SessionManager` used a plain `HashMap` accessed from multiple Ktor Netty threads. Concurrent requests could cause `ConcurrentModificationException` or silent data corruption. **Fix:** `ConcurrentHashMap` + `removeIf` instead of `removeAll`.

#### 4. Eliminated `runBlocking` in Time Limits
All four `RoomTimeLimitStore` methods used `runBlocking` to call Room DAO suspend functions. On the Mi Box (slow eMMC), cold queries could take 50-100ms, causing jank or ANR. **Fix:** Full suspend migration — `TimeLimitStore`, `WatchTimeProvider`, `TimeLimitManager` methods are all `suspend` now. 33 tests migrated to `runTest`.

#### 5. Version Bump
`build.gradle.kts` still said `0.7.0`. **Fix:** `versionCode = 11`, `versionName = "0.8.0"`.

### Tier 2: Public Release Hardening

#### 6. PlayEventRecorder Thread Safety
`PlayEventRecorder` singleton had mutable properties read from Ktor, UI, and IO threads with no `@Volatile`. **Fix:** All cross-thread properties annotated `@Volatile`.

#### 7. `/status` Endpoint Split
Previously exposed what the child is watching (video title, playlist, elapsed time) to anyone. **Fix:** Unauthenticated requests get only `version` + `serverRunning`. Authenticated requests get full response.

#### 8. DebugReceiver Moved to Debug Manifest
`DebugReceiver` was registered with `android:exported="true"` in the main manifest, shipping in release builds. **Fix:** Moved to `src/debug/AndroidManifest.xml`. Debug intents now wrapped in `scope.launch` for suspend calls.

#### 9. Timing-Safe PIN Comparison
`PinManager` used `pin == currentPin` (non-constant-time). **Fix:** `MessageDigest.isEqual()`.

#### 10. Security Headers on Dashboard
Neither Ktor nor relay set CSP, X-Frame-Options, or X-Content-Type-Options. **Fix:** Both now add `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` to all dashboard responses.

#### 11. Full Reset Video Deletion Bug
`handleFullReset()` called `videoDao().deleteByPlaylist("%")` — exact match, not LIKE. No videos were actually deleted. **Fix:** Added `deleteAll()` to `PlaylistCacheDao`.

#### 12. CORS on Ktor Server
No CORS plugin installed — any website on the LAN could make cross-origin requests. **Fix:** Ktor CORS plugin with `anyHost()` (LAN IPs vary), allowing GET/POST/PUT/DELETE with Authorization and Content-Type headers.

#### 13. Relay Durable Object Tests
`RelayDurableObject` had zero tests. **Fix:** 26 new tests covering WebSocket connect, secret rotation, heartbeat, alarm timeout, request bridging, and disconnect cleanup.

#### 14. ContentSourceRepository Tests
`buildCanonicalUrl()` and `extractVideoId()` had no test coverage. **Fix:** 8 new tests for URL parsing and canonical form construction.

#### 15. Dashboard XSS Prevention Tests
`escapeHtml()` worked correctly but had no automated proof. **Fix:** 4 new tests in `dashboard.test.ts` verifying script tags, quotes, ampersands, and safe content.

#### 16. Token Refresh in Dashboard
Dashboard didn't call `/auth/refresh` on page load despite CLAUDE.md saying it did. Sessions could silently expire. **Fix:** `refreshToken()` called at start of `loadDashboard()`.

### Tier 2.5: Deploy Confidence

#### Post-Deploy Smoke Tests
- **Emulator smoke** (`tv-app/scripts/deploy-smoke.sh`): 30 checks against running app — HTML sections, JS functions, CSS selectors, security headers, auth flow, API endpoints
- **Relay smoke** (`relay/test/deploy-smoke.sh`): 25+ checks against live relay — content verification, security headers, allowlist coverage, method enforcement

#### Route Alignment Test (`relay/test/route-alignment.test.ts`)
34 tests ensuring every Ktor API route has a relay allowlist entry. Would have caught the missing time-limit routes before deploy.

#### Relay Asset Symlinks
`relay/assets/` files are now symlinks to `tv-app/app/src/main/assets/` — eliminates copy drift.

#### Dashboard Parity Test
Vitest test verifying relay symlinks point to the correct local files.

#### Playwright Browser Tests
27 tests verifying dashboard rendering, auth flow, time controls UI, and responsiveness.

---

## Files Changed (50 files)

| Category | Files |
|----------|-------|
| **Spec + reviews** | `docs/v0.8-THE-AUDIT-SPEC.md`, `docs/reviews/` (4 files) |
| **Relay source** | `relay/src/allowlist.ts`, `relay/src/index.ts` |
| **Relay tests (new)** | `relay/test/relay-do.test.ts` (26), `relay/test/route-alignment.test.ts` (34), `relay/test/dashboard-parity.test.ts`, `relay/test/browser/dashboard.spec.ts` (27) |
| **Relay tests (updated)** | `relay/test/allowlist.test.ts`, `relay/test/dashboard.test.ts`, `relay/test/integration.test.ts`, `relay/test/security.test.ts` |
| **Relay config** | `relay/package.json`, `relay/playwright.config.ts`, `relay/vitest.config.ts`, `relay/vitest.parity.config.ts` |
| **Relay assets** | Symlinked to `tv-app/app/src/main/assets/` |
| **TV app source** | `ServiceLocator.kt`, `PinManager.kt`, `SessionManager.kt`, `PlayEventRecorder.kt`, `AuthRoutes.kt`, `DashboardRoutes.kt`, `ParentApprovedServer.kt`, `StatusRoutes.kt`, `DebugReceiver.kt`, `PlaylistCacheDao.kt` |
| **TV time limits** | `TimeLimitConfig.kt`, `TimeLimitManager.kt`, `RoomTimeLimitStore.kt`, `RoomWatchTimeProvider.kt` |
| **TV manifests** | `AndroidManifest.xml` (removed receiver), `src/debug/AndroidManifest.xml` (new) |
| **TV dashboard** | `app.js`, `index.html`, `style.css` |
| **TV build** | `build.gradle.kts` (version bump, CORS dep) |
| **TV assets** | `drawable-xhdpi/banner.png` (replaces `drawable/banner.xml`) |
| **TV tests (new)** | `ContentSourceRepositoryTest.kt` (8), `DashboardRoutesTest.kt`, `AuthRoutesTest.kt` (+2), `SessionManagerTest.kt` (+1), `StatusRoutesTest.kt` (+1), `TimeLimitManagerTest.kt` (+2) |
| **TV tests (updated)** | `TimeLimitManagerTest.kt` (33 → `runTest`), `StatusRoutesTest.kt`, `RelayBridgeIntegrationTest.kt` |
| **Scripts** | `tv-app/scripts/deploy-smoke.sh` (new), `relay/test/deploy-smoke.sh` (new), `tv-app/scripts/ci-run.sh` (updated) |
| **Docs** | `CLAUDE.md`, `RELEASE-v0.8.md`, `RETRO-v0.8.md` |

---

## Test Coverage

| Suite | v0.7.1 | v0.8 | Delta |
|-------|--------|------|-------|
| TV unit tests | 194 | 277 | +83 |
| TV instrumented | 19 | 19 | 0 |
| Relay vitest | 139 | 218 | +79 |
| Playwright browser | 0 | 27 | +27 |
| Landing page | 10 | 10 | 0 |
| Digest worker | 9 | 9 | 0 |
| **Total** | **371** | **560** | **+189** |

Plus script-based smoke tests: 30 (emulator) + 25 (relay).

---

## Deployment Order

1. **Deploy relay first** (`cd relay && npx wrangler deploy`) — additive allowlist changes, backward compatible
2. **Deploy TV APK second** — needs updated relay for time-limit remote access
