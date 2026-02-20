# ParentApproved.tv v0.8 Retrospective

**Date:** February 20, 2026
**Scope:** Post-deploy smoke tests + route alignment test to prevent "deploy gap" bugs

---

## What Went Well

### The Gap Was Obvious in Hindsight
The v0.8 audit revealed that no test verified *deployed* artifacts. Unit tests mock everything, Playwright uses source files, the old `e2e-smoke.sh` only checked HTTP status codes (200, 503, 404). Nobody asked "does the HTML served by the running app actually contain the screen-time section?" Once we framed it that way, the fix was straightforward.

### Route Alignment Test Is the Right Abstraction
Defining the canonical Ktor route list in one place and checking it against `isAllowed()` is simple, fast (34 tests in 12ms), and catches the exact class of bug that bit us: adding a Ktor route without updating the relay allowlist. The "known extras" pattern handles the legitimate cases (allowlist entries without Ktor handlers) without false positives.

### Shell Smoke Tests Are Low-Ceremony, High-Value
No test framework, no dependencies, just curl + grep. The emulator smoke test runs in seconds and catches content regressions that no unit test can. The `check_contains` / `check_header` helpers make it easy to add new checks.

### Debug Intents Made Auth Testing Possible
The emulator smoke test authenticates by getting the PIN via `DEBUG_GET_PIN` intent, then exercises the full auth → API flow. Without the debug intent system from v0.2, the smoke test would have to skip all authenticated endpoints.

---

## What Didn't Go Well

### `curl -sI` Sends HEAD, Ktor Only Handles GET
The first run of the emulator smoke test failed on all security header checks. `curl -sI` sends a HEAD request, and Ktor's `get("/")` handler doesn't respond to HEAD — returns 405 with no custom headers. Had to switch to `curl -s -D /tmp/headers` which uses GET and captures headers from the response.

**Lesson:** Ktor's GET routes don't automatically support HEAD. Use `curl -s -D` to capture headers from a GET request, not `curl -I`.

### Multiple ADB Devices Require Explicit Selection
The smoke test used `$ADB` without `-s emulator-5554`. With the Mi Box listed (even offline), adb refused to pick a device. Had to add a `DEVICE` parameter and wrap adb in a `adb_cmd()` function.

**Lesson:** Always parameterize the device in ADB scripts. Never assume a single connected device.

### Playwright Tests Can't Run in Cloudflare Workers Vitest Pool
The `test/browser/dashboard.spec.ts` Playwright test was picked up by the workers vitest config, which can't resolve `node:os`. Had to add `test/browser/**` to the exclude list. Playwright tests run via `npx playwright test`, not vitest.

**Lesson:** Keep browser tests in a directory excluded from the workers vitest config. They use a different runner.

---

## Learnings

1. **Test the artifact, not the source.** Unit tests verify logic, smoke tests verify the assembled product. Both are necessary. The smoke test caught a real gap that existed across 7 minor versions.

2. **Route alignment is a cross-layer concern.** The Ktor server and relay allowlist are in different languages, different repos-within-a-repo, different deploy pipelines. The alignment test is the only thing that bridges them.

3. **"Known extras" is better than strict 1:1 matching.** The relay allowlist intentionally has entries without Ktor handlers (`GET /api/playlists/:id`, `GET /api/playback`). A strict check would produce false failures. The capped extras list keeps it honest without being brittle.

4. **Smoke tests should be the last CI step.** They verify the deployed artifact, so they naturally come after build, unit tests, and instrumented tests. The CI runner now reflects this ordering.

---

## By the Numbers

- **3 new files** created (2 shell scripts, 1 vitest)
- **3 files modified** (ci-run.sh, vitest.config.ts, CLAUDE.md)
- **34 route-alignment tests** added
- **30 emulator smoke checks** per run
- **~25 relay smoke checks** per run
- **218 total relay vitest tests** (was 184)
- **2 bugs found during smoke test development** (HEAD vs GET, multi-device ADB)
- **1 haiku** — tradition holds
