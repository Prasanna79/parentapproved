# ParentApproved.tv v0.8 "The Audit" — Release Notes

**Date:** February 20, 2026
**Milestone:** Deploy with confidence. Smoke tests verify the running artifact, not just the source.

```
Smoke curls upward —
deployed code, now tested,
trust what's actually served.
```

---

## What's New

### Post-Deploy Smoke Tests

v0.8 revealed that the relay dashboard was missing time controls after deploy. Unit tests and Playwright tested source files, not deployed artifacts. These new smoke tests close that gap.

#### Emulator Deploy Smoke (`tv-app/scripts/deploy-smoke.sh`)
Runs against the live app on emulator via HTTP. Verifies 30 checks:
- `/status` returns `serverRunning` and version
- Dashboard HTML contains all required section IDs (`auth-screen`, `dashboard`, `screen-time-section`, `playlists-section`, `stats-section`, `recent-section`, `now-playing`, `edit-limits-modal`)
- Security headers present (CSP, X-Content-Type-Options, X-Frame-Options)
- `app.js` contains key functions (`loadTimeLimits`, `toggleLock`, `grantBonusTime`, `openEditLimits`, `saveLimits`, `loadDashboard`, `refreshToken`)
- `style.css` contains key selectors (`.screen-time-card`, `#st-lock-btn`, `.modal-overlay`, `.st-controls`)
- Favicon serves, legacy `/assets/*` paths work
- Auth flow: gets PIN via debug intent, authenticates, verifies `/time-limits`, `/playlists`, `/stats` all return 200

#### Relay Deploy Smoke (`relay/test/deploy-smoke.sh`)
Runs against a live relay URL. Replaces the old `e2e-smoke.sh` (5 checks) with 20+ checks:
- All original checks (dashboard 200, no-TV 503, non-allowlisted 404, oversize 413, bad method 405)
- Dashboard HTML/JS/CSS content verification (same section IDs, functions, selectors as emulator)
- Security headers (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy)
- Time-limit routes accepted by allowlist (503 not 404)
- Auth route forwarded (503 not 404)
- PATCH method blocked (405)

### Route Alignment Test (`relay/test/route-alignment.test.ts`)
Automated check that every Ktor API route has a matching relay allowlist entry. This is the test that would have caught the missing time-limit routes *before* deploy. 34 tests covering:
- Every Ktor route passes `isAllowed()` (16 routes)
- Every allowlist entry maps to a real Ktor handler (16 entries + 2 documented extras)
- No Ktor route is missing from the allowlist (aggregate check)
- Known extras list stays small (max 3)

### CI Runner Updated
`ci-run.sh` now includes three new steps after existing ones:
- Step 7: Emulator deploy smoke
- Step 8: Relay vitest (includes route-alignment)
- Step 9: Playwright browser tests

### CLAUDE.md Deploy Verification Section
New "Deploy Verification (Required)" section documents the 6-step verification chain and rules for dashboard/route changes.

---

## Files Changed

| File | Action | Details |
|------|--------|---------|
| `tv-app/scripts/deploy-smoke.sh` | NEW | 30-check emulator smoke test |
| `relay/test/deploy-smoke.sh` | NEW | 20+ check relay smoke test (replaces `e2e-smoke.sh`) |
| `relay/test/route-alignment.test.ts` | NEW | 34 Ktor ↔ allowlist alignment tests |
| `tv-app/scripts/ci-run.sh` | UPDATED | Added steps 7-9 |
| `relay/vitest.config.ts` | UPDATED | Excluded `test/browser/**` from workers pool |
| `CLAUDE.md` | UPDATED | Added Deploy Verification section |

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| Route alignment | 34 | `cd relay && npx vitest run` |
| Emulator deploy smoke | 30 | `bash tv-app/scripts/deploy-smoke.sh` |
| Relay deploy smoke | ~25 | `bash relay/test/deploy-smoke.sh <URL>` |
| **Relay total (with new)** | **252** | `cd relay && npx vitest run` |

---

## Usage

```bash
# Emulator smoke (app must be running)
bash tv-app/scripts/deploy-smoke.sh [DEVICE] [PORT]
# defaults: emulator-5554, 8080

# Relay smoke (against deployed relay)
bash relay/test/deploy-smoke.sh https://relay.parentapproved.tv
```
