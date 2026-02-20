# ParentApproved.tv Dev Manager Review

**Date:** 2026-02-20
**Reviewer:** Dev Manager (Claude Opus 4.6)
**Inputs:** Architecture Review, Security Review, Test Coverage Gap Analysis
**Version:** v0.7.1 (commit 547db0a)

---

## Executive Verdict

ParentApproved.tv is a well-built side project that punches above its weight in architecture and test discipline. The three reviews surfaced 0 CRITICAL (security) / 1 CRITICAL (architecture, session leak) / 2 HIGH (security) / 4 HIGH (architecture) findings, plus significant test gaps in the dashboard and relay Durable Object. However, the threat model is bounded -- this is a family app on a home LAN, not a banking platform. The session leak is a resource bug, not a security hole. The PIN timing side-channel is theoretical at best on a WiFi network. The real blockers for public release are few and small: fix the double session creation, add time-limit routes to the relay allowlist, and address the thread safety in SessionManager. Everything else is "should fix" or "nice to have." My recommendation: **ship to friends/family now, fix Tier 1 items (2-3 person-days), then open to public.**

---

## Cross-Cutting Themes

### Theme 1: Relay Allowlist Missing Time-Limit Routes

**All three reviewers flagged this.** Architecture (Section 4, HIGH), Security (Finding 6.1, MEDIUM), Test Coverage (Section 4.3, P0-CRITICAL).

This is the single most impactful functional defect. Time controls were the v0.7.0 headline feature ("The Clock"), and they do not work over remote access. The fix is trivial (add 5 routes + PUT method to `allowlist.ts`), but the fact that all three reviewers caught it independently tells me there is no integration test covering "parent manages time limits via relay." That is the real gap.

**Verdict:** Agree this blocks public release. The fix is 1 hour of code + 1 hour of testing.

### Theme 2: Thread Safety in Shared Mutable State

Architecture flagged SessionManager (HIGH), PlayEventRecorder (HIGH), TimeLimitRoutes closure state (MEDIUM). Security did not flag these. Test Coverage did not flag these.

This is a class of bug that only manifests under concurrent load, which is unlikely in a single-household app (one parent phone, one TV). But "unlikely" is not "impossible" -- a parent refreshing the dashboard rapidly while the kid is watching could hit SessionManager from two Ktor threads simultaneously.

**Verdict:** SessionManager to ConcurrentHashMap is a 15-minute fix -- do it. PlayEventRecorder volatility is also quick. TimeLimitRoutes closure state can wait; concurrent time-limit requests from two phones is an edge case that does not justify the effort right now.

### Theme 3: `/status` Endpoint Unauthenticated

Architecture flagged it (LOW). Security flagged it (HIGH, with privacy angle about child viewing data). Test Coverage noted the route is tested but has no unauth test.

The security reviewer's framing is correct: this leaks what a child is watching to anyone on the LAN or anyone who discovers the tvId. However, the practical risk is low. On a home LAN, the "attackers" are other family members or house guests. Via relay, the tvId is a UUID (122 bits of entropy) -- you cannot enumerate them.

**Verdict:** Downgrade from HIGH to MEDIUM for this project's context. The right fix is the split response: unauthenticated gets `{serverRunning, version}`, authenticated gets the full payload. Do it in Tier 2, not Tier 1.

### Theme 4: Dashboard and Relay DO Have Zero Meaningful Tests

Test Coverage flagged dashboard (P0, HIGH risk) and Durable Object (P0, HIGH risk). Architecture noted the dashboard path prefix issue. Security noted the lack of CSP headers.

The dashboard is 480 lines of vanilla JS. The relay DO is the critical bridge for remote access. Neither has real tests. However, these components have been working in manual testing since v0.4 (relay) and v0.1 (dashboard). The risk of shipping without tests is not that the code is broken -- it is that future changes could break it silently.

**Verdict:** For friends/family release, the lack of tests is acceptable -- the code works. For public release, add at least the P0 tests (relay DO connect/reject/bridge, dashboard XSS prevention). Estimate: 3-4 days.

### Theme 5: `runBlocking` on UI Thread

Architecture flagged this (HIGH). Neither security nor test coverage flagged it.

`runBlocking` in `RoomTimeLimitStore` called from Compose `LaunchedEffect` can cause ANR if Room hits cold disk I/O. On the Mi Box (Android 9, eMMC storage), this is plausible. On the emulator, it will never manifest.

**Verdict:** This is a real bug that will bite on real hardware. Promote to Tier 1. The fix (make TimeLimitStore suspend or wrap in `withContext(Dispatchers.IO)`) touches multiple call sites but is straightforward. Estimate: 2-3 hours.

### Theme 6: Findings One Reviewer Caught That Others Missed

| Finding | Caught By | Missed By | Comment |
|---------|-----------|-----------|---------|
| Double session creation | Architecture (CRITICAL) | Security, Test Coverage | Test Coverage has tests for session creation but they test AuthRoutes and SessionManager separately, not the callback wiring |
| PIN timing side-channel | Security (HIGH) | Architecture, Test Coverage | Theoretical on LAN; relay already uses timingSafeEqual |
| DebugReceiver in prod manifest | Security (MEDIUM) | Architecture, Test Coverage | Architecture noted it as appropriate; security correctly flags the export |
| No CORS on Ktor | Security (MEDIUM) | Architecture, Test Coverage | Valid but low practical risk -- requires malicious JS on LAN |
| `handleFullReset` video deletion bug | Architecture (MEDIUM) | Security, Test Coverage | Debug-only code path, but still a correctness bug |
| Version mismatch (0.7.0 vs 0.7.1) | Architecture (MEDIUM) | Security, Test Coverage | Housekeeping |
| No CI/CD | Test Coverage (P1) | Architecture, Security | Expected for a side project at this stage |

---

## Tier 1: Must Fix Before ANY Public Release

These are the true blockers. Anything not on this list can ship as a known issue.

| # | Finding | Source | Effort | Why It Blocks |
|---|---------|--------|--------|---------------|
| T1.1 | Double session creation on PIN auth | Arch CRITICAL | 0.5h | Resource leak that degrades over time; 20 logins = 10 wasted session slots |
| T1.2 | Relay allowlist missing time-limit routes | Arch HIGH, Sec MEDIUM, Test P0 | 2h | Headline feature (v0.7.0) does not work remotely |
| T1.3 | SessionManager thread safety (ConcurrentHashMap) | Arch HIGH | 0.25h | ConcurrentModificationException under load |
| T1.4 | `runBlocking` in RoomTimeLimitStore | Arch HIGH | 3h | ANR on real hardware (Mi Box) |
| T1.5 | Version mismatch in build.gradle.kts | Arch MEDIUM | 0.1h | App self-reports wrong version |

**Total Tier 1 effort: ~6 hours (< 1 person-day)**

---

## Tier 2: Should Fix Before Wider Distribution

These improve quality and security posture but do not block a limited public release.

| # | Finding | Source | Effort | Notes |
|---|---------|--------|--------|-------|
| T2.1 | PlayEventRecorder thread safety | Arch HIGH | 1h | Add @Volatile to mutable properties |
| T2.2 | Authenticate `/status` endpoint (split response) | Sec HIGH, Arch LOW | 2h | Privacy improvement |
| T2.3 | Move DebugReceiver to debug manifest | Sec MEDIUM | 0.5h | Defense in depth; `IS_DEBUG` guard is sufficient for now |
| T2.4 | PIN timing-safe comparison | Sec HIGH | 0.25h | Trivial fix, good hygiene |
| T2.5 | Add CSP and security headers | Sec MEDIUM | 1h | Standard web security headers |
| T2.6 | `handleFullReset` video deletion bug | Arch MEDIUM | 0.25h | Debug-only but correctness matters |
| T2.7 | Dashboard session token refresh | Arch MEDIUM | 1h | Sessions expire after 90 days; low urgency |
| T2.8 | Relay DO core tests (connect/reject/bridge/timeout) | Test P0 | 3d | Biggest test gap; needed before relay changes |
| T2.9 | ContentSourceRepository pure function tests | Test P0 | 0.5h | Low-hanging fruit |
| T2.10 | Dashboard XSS prevention tests | Test P0 | 0.5h | Verify escapeHtml works; it does, but prove it |
| T2.11 | Add CORS to Ktor | Sec MEDIUM | 0.5h | Low practical risk but easy fix |

**Total Tier 2 effort: ~5-6 person-days** (dominated by relay DO test infrastructure)

---

## Tier 3: Known Issues / Tech Debt for Future Releases

These are real findings but do not affect the user experience or security posture in a meaningful way at current scale.

| # | Finding | Source | Effort | Notes |
|---|---------|--------|--------|-------|
| T3.1 | StatusRoutes direct ServiceLocator access | Arch MEDIUM | 0.5h | Code hygiene |
| T3.2 | No API versioning | Arch MEDIUM | - | Design decision, not a bug |
| T3.3 | Inconsistent response formats | Arch MEDIUM | 2h | String "true" vs boolean true |
| T3.4 | Dependency version updates | Arch MEDIUM | 4h+ | Ktor 3.x migration is non-trivial |
| T3.5 | Enable ProGuard/R8 for release | Arch MEDIUM, Sec LOW | 4h | Needs ProGuard rules for NewPipe, Ktor |
| T3.6 | Dashboard relay path prefix handling | Arch MEDIUM | 2h | Verify it actually works first |
| T3.7 | `usesCleartextTraffic` restriction | Sec MEDIUM | 0.5h | Network security config |
| T3.8 | EncryptedSharedPreferences | Sec LOW | 2h | Defense in depth for on-device secrets |
| T3.9 | CI/CD pipeline | Test P1 | 2d | GitHub Actions for all 5 suites |
| T3.10 | Dashboard full test suite (JSDOM) | Test P1-P2 | 3d | 25-30 tests for auth, playlist, time limits UI |
| T3.11 | Compose UI tests | Test P2 | 3d | Low ROI for current project |
| T3.12 | Relay Worker entry point tests | Test P0 | 2d | Important but Worker is thin routing layer |
| T3.13 | Ktor server lifecycle (Activity -> Application) | Arch LOW | 2h | Edge case on Activity recreation |
| T3.14 | Per-TV localStorage keys | Arch LOW | 0.5h | Multi-TV households |
| T3.15 | PIN in QR code URL (clear after use) | Sec LOW | 0.1h | One-liner `history.replaceState` |
| T3.16 | Room schema export | Arch LOW | 0.5h | Future migration safety |
| T3.17 | Naming inconsistency (Playlist/Channel/Source) | Arch LOW | - | Cosmetic; document and move on |

---

## Specific Comments on Individual Findings

### Architecture CRITICAL: Double Session Creation

Agree with severity. The fix is obvious: use the token from `PinResult.Success.token` in `AuthRoutes` instead of calling `createSession()` again. The callback pattern in ServiceLocator is the root cause -- `onPinValidated` should not create a session; it should just signal success. The architect's 30-minute estimate is realistic.

### Security HIGH: PIN Timing Side-Channel (Finding 1.1)

**Disagree with HIGH severity for this project.** A timing attack against a 6-digit PIN over WiFi requires thousands of measurements per digit, sub-microsecond timing resolution through network jitter, and a motivated attacker on the same LAN. The rate limiter (5 attempts before lockout with exponential backoff) makes this practically unexploitable. The CVSS score of 5.3 assumes "high confidentiality impact if PIN is leaked" but the PIN is displayed on the TV screen -- anyone in the room can see it.

That said, the fix is `MessageDigest.isEqual()` -- a one-line change. Do it for hygiene (Tier 2), not because it is a real risk.

### Security HIGH: Unauthenticated `/status` Endpoint (Finding 1.2)

The security reviewer correctly identifies the privacy angle (child viewing data). The architecture reviewer rated this LOW. I split the difference: MEDIUM. The `/status` endpoint is useful for connection testing (dashboard shows "connected" indicator before auth), so removing auth entirely would break UX. The split-response approach is right.

The relay exposure is more concerning than the LAN exposure. Anyone who obtains a tvId can poll what the child is watching. tvId is a UUID so blind guessing is infeasible, but if it leaks (e.g., in a screenshot of the connect screen), it becomes a permanent surveillance token. Consider: should `/status` be removed from the relay allowlist entirely? The relay dashboard can use `/auth/refresh` to test connectivity instead.

### Security MEDIUM: DebugReceiver in Prod Manifest (Finding 5.1)

Agree with severity and fix. The `IS_DEBUG` guard is sufficient protection, but shipping a registered broadcast receiver in a release APK is sloppy. Moving to `src/debug/AndroidManifest.xml` is the clean solution. Note: this means `DebugReceiverIntentTest` (12 instrumented tests) needs to run against the debug build variant, which it already does.

### Test Coverage P0: Relay Durable Object Tests

The test manager rates this P0, and I agree it is the biggest gap. However, I **disagree that it blocks friends/family release.** The relay has been working since v0.4 (4 versions ago). The risk is not "it is broken" but "we cannot safely change it." For a public release where we might need to hotfix the relay, having DO tests becomes essential.

**Recommendation:** Start DO test infrastructure now. It is the long pole in the testing plan (~3 days). Do not gate friends/family release on it, but gate the next relay deployment on it.

### Test Coverage P0: Dashboard Tests

Similar reasoning. The dashboard works. It has been manually tested since v0.1. But it has zero automated tests, and it is the primary user-facing surface. The risk is regression, not current breakage.

**Recommendation:** Add `escapeHtml` unit test (15 minutes, Tier 2). Defer full JSDOM test suite to Tier 3. The ROI of 3 days of JSDOM infrastructure for a 480-line JS file is marginal.

### Architecture HIGH: `runBlocking` in RoomTimeLimitStore

This is the finding I am most concerned about for real-world usage. `runBlocking` inside a coroutine launched from `LaunchedEffect` can deadlock or ANR. On the Mi Box with its slow eMMC storage, a cold Room query could take 50-100ms -- enough to drop frames and feel janky, though not enough for a true ANR (5s threshold). On repeated calls (polling), the cumulative effect could be worse.

The fix requires changing the `TimeLimitStore` interface from synchronous to suspend, which ripples to `TimeLimitManager` and its 33 unit tests. Architect estimated 2 hours; I think 3 hours is more realistic with test updates.

---

## Dependency Mapping

```
T1.1 (double session) -- standalone, no dependencies
T1.2 (relay allowlist) -- standalone, deploy relay first
T1.3 (SessionManager ConcurrentHashMap) -- standalone
T1.4 (runBlocking fix) -- touches TimeLimitStore interface, TimeLimitManager,
                           RoomTimeLimitStore, and 33 TimeLimitManagerTests
T1.5 (version bump) -- standalone, do last before release build

T2.2 (status auth) depends on nothing but changes StatusRoutes and dashboard
T2.8 (relay DO tests) depends on Miniflare DO test infrastructure (new)
T2.4 (timing-safe PIN) -- standalone
```

Critical path for Tier 1: **T1.4 is the longest item (3h) and has the most test updates.** Everything else is < 1 hour. A single developer can complete all Tier 1 items in one focused day.

Critical path for Tier 2: **T2.8 (relay DO tests) is the long pole at 3 days.** Everything else in Tier 2 is < 2 hours each. Start T2.8 early if aiming for a quick follow-up release.

---

## Release Readiness Verdict

### Friends/Family Release: GO

Ship as-is. The app works. The bugs are resource leaks and edge cases, not data loss or security breaches. The people using it (your family, your friends' families) will not encounter the thread safety issues with one phone and one TV. The relay time-limit gap is the most user-visible issue -- mention it in release notes ("time limits currently require LAN access; remote support coming in v0.7.2").

### Limited Public Release: GO after Tier 1

Fix the 5 Tier 1 items (~1 person-day). The double session leak and runBlocking ANR are the only items that could cause user-visible problems at small scale. The relay allowlist fix enables the full feature set over remote access.

### Wider Public Release: After Tier 1 + Tier 2

The relay DO tests (T2.8) and security hardening (T2.2-T2.5) are needed before the app is in the hands of users you do not know personally. If someone finds a tvId, they should not be able to see what a child is watching (T2.2). The relay needs tests before you can safely deploy hotfixes (T2.8).

---

## Effort Summary

| Tier | Items | Effort | Timeline |
|------|-------|--------|----------|
| Tier 1 | 5 items | ~1 person-day | Before limited public release |
| Tier 2 | 11 items | ~5-6 person-days | Before wider distribution |
| Tier 3 | 17 items | ~20+ person-days | Ongoing tech debt |

**Recommended sequencing:**
1. Day 1: All Tier 1 items. Build, test, deploy relay, ship APK.
2. Days 2-4: T2.8 (relay DO test infrastructure) in parallel with T2.1-T2.6 (quick fixes).
3. Day 5-6: T2.7, T2.9-T2.11. Cut v0.7.2 release.
4. Ongoing: Tier 3 items as natural refactoring during feature work.

---

## Areas of Agreement Across All Three Reviews

1. **The architecture is sound.** All reviewers praised the local-first design, clean component boundaries, and interface-driven testing.
2. **The relay allowlist gap is real and must be fixed.** Universal agreement.
3. **The test discipline is strong for a side project.** 371 tests across 5 suites, with injectable clocks and fake DAOs.
4. **Dependencies are pinned and reasonably current.** No CVEs, no supply chain concerns.
5. **NewPipeExtractor is a known risk.** All reviewers acknowledged it. No mitigation needed beyond monitoring.

## Areas of Disagreement

1. **`/status` auth:** Architecture says LOW, Security says HIGH. I say MEDIUM. Context matters.
2. **PIN timing attack:** Security says HIGH (CVSS 5.3). I say LOW in practice. Fix it anyway because it is trivial.
3. **Dashboard tests as release blocker:** Test Coverage says P0. I say defer full suite; add one XSS test.
4. **Relay DO tests as release blocker:** Test Coverage says P0. I say needed for next relay deploy, not for current release.

---

*Review completed 2026-02-20. Three input reviews synthesized into prioritized action plan.*
