# ParentApproved.tv v0.9.3 — Release Notes

**Date:** February 23, 2026
**Milestone:** Dependency upgrades and mandatory test gate.

```
old jars swept clean —
one command runs every test,
no suite left behind.
```

---

## What's New

v0.9.3 upgrades all dependencies to current versions and adds a single mandatory test gate (`ci-run.sh verify`) that runs all 560 automated tests before any merge or release. No more cherry-picking which test suites to run.

### Dependency Upgrades

| Package | From | To |
|---------|------|----|
| Ktor | 2.3.7 | 3.1.1 |
| Compose BOM | 2025.01.01 | 2026.02.00 |
| Room | 2.6.1 | 2.8.4 |
| Media3 (ExoPlayer) | 1.5.1 | 1.9.2 |
| AGP | 8.7.3 | 8.13.0 |
| Gradle | 8.9 | 8.14.4 |
| wrangler | 4.66.0 | 4.67.0 |
| @cloudflare/workers-types | 4.20260217.0 | 4.20260228.0 |
| @cloudflare/vitest-pool-workers | 0.8.71 | 0.12.14 |
| vitest (relay) | 3.0.9 | 3.2.4 |
| vitest (landing, digest) | 3.2.4 | 4.0.18 |

### Ktor 2 → 3 Migration

Breaking changes fixed:
- `ApplicationEngine` → `EmbeddedServer<*, *>` (server type)
- `PipelineContext<Unit, ApplicationCall>` → `RoutingContext` (route handler receiver)
- `server.resolvedConnectors()` → `server.engine.resolvedConnectors()`

### Mandatory Test Gate

New `verify` suite in `ci-run.sh` runs all automated tests in one command:

```bash
bash tv-app/scripts/ci-run.sh verify
```

Runs: unit (302) + instrumented (19) + relay (220) + landing (10) + digest (9) = **560 tests**.
Requires emulator. Does not require app launched. `set -euo pipefail` stops on first failure.

### CI Fix for Dependabot

Firebase Test Lab steps in `build.yml` now skip when secrets are unavailable (Dependabot PRs), so the rest of CI still provides signal.

---

## Files Changed

| Category | Files |
|----------|-------|
| **Ktor 3 migration** | `tv-app/app/build.gradle.kts`, `tv-app/app/src/main/java/tv/parentapproved/app/server/ParentApprovedServer.kt`, `tv-app/app/src/main/java/tv/parentapproved/app/server/AuthRoutes.kt`, `tv-app/app/src/test/java/tv/parentapproved/app/relay/RelayBridgeIntegrationTest.kt` |
| **Gradle/AGP** | `tv-app/build.gradle.kts`, `tv-app/gradle/wrapper/gradle-wrapper.properties` |
| **Relay deps** | `relay/package.json`, `relay/package-lock.json` |
| **Marketing deps** | `marketing/landing-page/package.json`, `marketing/landing-page/package-lock.json`, `marketing/notify-digest/package.json`, `marketing/notify-digest/package-lock.json` |
| **Test gate** | `tv-app/scripts/ci-run.sh`, `CLAUDE.md` |
| **CI** | `.github/workflows/build.yml` |

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| TV unit tests | 302 | `./gradlew testDebugUnitTest` |
| TV instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| Relay tests | 220 | `cd relay && npx vitest run` |
| Landing page tests | 10 | `cd marketing/landing-page && npx vitest run` |
| Digest worker tests | 9 | `cd marketing/notify-digest && npx vitest run` |
| Playwright deploy smoke | 11 | `bash tv-app/scripts/ci-run.sh playwright-smoke` |
| **Total** | **571** | |

---

## Deployment

No new features — dependency upgrades and test infrastructure only. All 560 automated tests pass. All 19 instrumented tests pass on emulator (TV_API34, API 34) and Firebase Test Lab.
