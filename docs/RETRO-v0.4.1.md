# ParentApproved.tv v0.4.1 Retrospective

**Date:** February 18, 2026
**Scope:** Full rebrand (package, UI, relay, scripts) + landing page editorial pass

---

## What Went Well

### The Rebrand Was Mechanical, Not Surgical
No logic changed. No features added. No behavior modified. The entire rebrand was find-and-replace plus file moves. This is the reward of having a clean architecture — renaming the package didn't require rethinking anything.

### Git Detected All 75 Moves as Renames
`cp` + `rm` of directories (not `git mv`) still resulted in git correctly detecting every file as a rename with 75-98% similarity. Full blame history preserved. Worried for nothing.

### The Audit File Paid Off Immediately
Writing `v041-REBRAND-AUDIT.md` before touching code — with every file, line number, and category — turned a scary "rename everything" task into a checklist. No hunting, no guessing, no "did I miss one." Category 1/2/3 classification (must change / keep / update later) prevented scope creep.

### Old App Crash Caught Immediately
The old `com.kidswatch.tv` was still running on the emulator, holding port 8080. The new `tv.parentapproved.app` crashed with `Address already in use`. Uninstalling the old app fixed it instantly. This would have been confusing without the logcat trace showing both package names.

### Pre-existing Test Bug Surfaced
`SessionManagerTest.maxConcurrentSessions_rejects6th` was silently failing — the limit was bumped to 20 in v0.4 but the test still expected 5. The rebrand's test run caught it because we actually ran all 187 tests with fresh eyes.

### Landing Page Mom-Persona Review
Reading the page as "Jess, 25, with a 3-year-old" caught 17 issues that a developer eye wouldn't. The user's item-by-item approval/modification of each recommendation was the right workflow — no wasted effort on changes that wouldn't land.

---

## What Didn't Go Well

### Logcat Tag Filter Was Unreliable on API 34
After the rebrand, `logcat -s ParentApproved-Intent` returned nothing, even though broadcasts were received (confirmed via ActivityManager logs). This is the same API 34 logcat issue from v0.4 — `Log.d()` output silently suppressed. The test-suite.sh script works around this with broader grep patterns, but manual debugging is painful.

**Same lesson as v0.4:** Switch debug intents to `Log.i()` or `Log.w()`. Still not done.

### "Phase 3: DO NOT Change" Became "Change Everything"
The audit carefully categorized package names, class names, SharedPrefs keys, and Room DB names as "DO NOT change — breaks existing installs." Then the user said "we have no existing installs." The categories were correct for a shipped product but wrong for a pre-launch beta. Time spent on the careful categorization wasn't wasted (it's good documentation) but the initial framing was overcautious.

**Lesson:** Ask about deployment state before designing migration constraints. "Is anyone running this?" changes everything.

### 7 Flaky UI Tests Still Flaky
The same 7 UI tests that fail intermittently (Back button, playlist row timing, Settings buttons off-screen) continue to fail. They're not rebrand-related, but they erode confidence in the test suite. Every time they fail, someone has to manually verify "is this real or flaky?"

**Lesson:** Fix or delete flaky tests. A test that fails randomly and is always ignored is worse than no test — it trains you to dismiss failures.

---

## Learnings

1. **Write the audit before the refactor.** Listing every occurrence with file, line, and category before touching code turns a risky refactor into a boring checklist. The 30 minutes spent writing the audit saved hours of "did I miss something?"

2. **Uninstall the old package before installing the new one.** Two apps with different package names but the same port binding will crash. When renaming `applicationId`, the old APK doesn't magically disappear from the device.

3. **Git handles directory moves fine.** You don't need `git mv`. Copy to new location, delete old, `git add -A` — git's rename detection (based on content similarity) correctly identifies them as moves. History preserved.

4. **Pre-existing test failures surface during big changes.** The rebrand didn't break the session test — it was already broken. But running the full suite with "everything should pass" attention caught what "just run the tests" complacency missed.

5. **Persona reviews catch different bugs than developer reviews.** "Do I need a YouTube account or API key?" is a developer question. "Do I need to sign in to anything?" is a parent question. Same answer, completely different framing. The mom persona caught 17 of these.

6. **FAQ ordering matters.** "How long does setup take?" belongs above "What is the relay?" Parents scan top-to-bottom and bail if the first few questions aren't relevant to them.

---

## By the Numbers

- **75 Kotlin files** moved to new package
- **94 files** changed in the rebrand commit
- **0 logic changes** — pure rename
- **187 unit tests** pass (1 pre-existing fix)
- **15/15 intent + HTTP tests** pass
- **27/34 UI tests** pass (7 pre-existing flaky)
- **23 FAQ questions** on landing page (was 17)
- **13 questions** in JSON-LD schema (was 12)
- **2 commits** — landing page editorial + full rebrand
- **1 haiku** — tradition holds

---

## v0.5 Priorities (from this retro)

| Priority | Item | Why |
|----------|------|-----|
| P0 | Screenshots for landing page | Can't go live without real images showing "ParentApproved" |
| P0 | Cloudflare Pages deployment | Domain is registered, page is built, just needs hosting |
| P1 | Fix 7 flaky UI tests | Eroding trust in the test suite |
| P1 | `Log.d` → `Log.i` for debug intents | Silent on API 34+ for the second version running |
| P2 | UI color/skin improvements | Landing page has a visual identity; TV app should match |
| P2 | Email signup backend (Buttondown or similar) | Form on landing page submits to nowhere |
