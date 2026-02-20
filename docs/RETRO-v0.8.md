# ParentApproved.tv v0.8 Retrospective

**Date:** February 20, 2026
**Scope:** Quality and correctness release driven by four independent code reviews. 16 fixes, 0 new features, +189 tests.

---

## What Went Well

### Four Independent Reviews Were Worth It
Architecture, security, test coverage, and dev manager perspectives each found different things. The architecture reviewer caught the `runBlocking` ANR risk. The security reviewer caught the `/status` privacy leak and timing-safe PIN gap. The test coverage reviewer found the zero-test Durable Object. The dev manager prioritized ruthlessly. Four views, one coherent plan.

### The Spec Was Surgical
`v0.8-THE-AUDIT-SPEC.md` specified exact file paths, line numbers, before/after code blocks, and acceptance criteria for every change. This eliminated ambiguity — implementation was mostly mechanical. Each fix took less time than finding it.

### Suspend Migration Was Smoother Than Expected
Converting `TimeLimitStore`/`WatchTimeProvider`/`TimeLimitManager` from blocking to suspend touched 33 tests and 8 source files. The compiler guided every callsite. `runTest {}` was a drop-in wrapper. No runtime surprises.

### Relay Assets as Symlinks Solved Copy Drift Permanently
Previous versions manually copied dashboard files between `tv-app/app/src/main/assets/` and `relay/assets/`. The v0.8 parity test and symlink approach means divergence is now impossible — there's literally one copy of each file.

### 26 Durable Object Tests in One Session
The DO was the most critical untested code. WebSocket connect/disconnect, secret rotation, heartbeat timeout, request bridging — all tested with Miniflare's `runInDurableObject`. Going from 0 to 26 tests gave immediate confidence.

---

## What Didn't Go Well

### Relay Allowlist Gap Shipped for 2 Weeks
The v0.7.0 "The Clock" feature (time controls) was broken on the relay from day one. No test verified that new Ktor routes had matching allowlist entries. The route-alignment test would have caught this in CI, but it didn't exist. This is the single most impactful gap in v0.8.

**Lesson:** Route alignment is a cross-layer invariant. It needs its own test, not just "we'll remember to update both."

### `curl -sI` vs Ktor HEAD Support
The emulator smoke test initially used `curl -sI` (HEAD request) to check security headers. Ktor's GET routes don't support HEAD — returns 405 with no custom headers. Wasted a debug cycle before switching to `curl -s -D`.

**Lesson:** Ktor GET routes don't auto-support HEAD. Use `curl -s -D` for header checks.

### Multiple ADB Devices Broke Smoke Test
The smoke test used `$ADB` without a device selector. With the Mi Box listed (even offline), adb refused to pick a device. Had to add explicit device parameterization.

**Lesson:** Always parameterize the device in ADB scripts.

### Playwright in Workers Vitest Pool
Playwright's `test/browser/dashboard.spec.ts` was picked up by the Cloudflare Workers vitest config, which can't resolve `node:os`. Had to exclude `test/browser/**`.

**Lesson:** Browser tests and Workers pool tests need separate configs. Keep them in clearly separated directories.

### Dashboard Had No Version/Feature Manifest
The smoke tests check for specific HTML IDs, JS functions, and CSS selectors — but there's no single source of truth for "what the dashboard must contain." The lists in `deploy-smoke.sh` are manually curated. If someone adds a dashboard section, they need to know to update the smoke test.

**Lesson:** Consider a `DASHBOARD_MANIFEST` constant that both the smoke tests and the code reference.

---

## Learnings

1. **Review-driven releases are efficient.** The spec was 625 lines and covered 16 changes. Implementation took one focused day. The reviews took longer to write than the code took to fix.

2. **Cross-layer invariants need dedicated tests.** The Ktor ↔ relay allowlist gap existed for 2 weeks because nothing verified the relationship. Route-alignment tests are cheap (34 tests in 12ms) and high-value.

3. **Test the artifact, not the source.** Unit tests verify logic. Smoke tests verify the assembled product. Both are necessary. The deploy smoke tests would have caught the missing relay time-controls on first deploy.

4. **Suspend migration is safe with compiler guidance.** Every callsite that needed updating failed to compile. The Kotlin compiler is effectively a migration tool for blocking → suspend conversions.

5. **Symlinks beat copy-on-change.** For files that must stay identical (dashboard assets), symlinks eliminate the failure mode entirely. The parity test is belt-and-suspenders.

6. **`@Volatile` is the minimum for cross-thread reads.** `PlayEventRecorder` was accessed from 3 threads with no synchronization. `@Volatile` doesn't solve everything, but it prevents stale reads at zero runtime cost.

7. **`MessageDigest.isEqual()` is Java's timing-safe compare.** No external dependency needed. Drop-in replacement for `==` on byte arrays.

---

## By the Numbers

- **50 files** changed
- **16 findings** resolved (5 Tier 1, 11 Tier 2)
- **189 new tests** (83 TV unit + 79 relay vitest + 27 Playwright)
- **560 total tests** (was 371)
- **33 tests** migrated from blocking to `runTest`
- **0 new features** — purely correctness and hardening
- **4 independent reviews** drove the spec
- **2 weeks** the relay allowlist gap existed before detection
- **1 haiku** — tradition holds
