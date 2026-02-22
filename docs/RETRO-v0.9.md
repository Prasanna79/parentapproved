# ParentApproved.tv v0.9 Retrospective

**Date:** February 22, 2026
**Scope:** Friends-and-family distribution release. Signing, CI/CD, download page, crash visibility, update checker, open source. +27 tests.

---

## What Went Well

### The Spec Was the Right Scope
12 changes, zero new viewing features. Each change had a clear "why" tied to distribution: signing (so APKs can update in-place), crash handler (so we see problems), update checker (so users don't stay stale), CI (so builds are reproducible). No scope creep.

### Firebase Test Lab in CI Works
19 instrumented tests run on every PR and before every release. Cost is ~$0.08/run — under $5/year at daily pushes. The setup was painful (see below) but the result is solid: instrumented tests are no longer "I'll remember to run them locally."

### CI-Built APK Verified End-to-End
Downloaded the CI artifact, verified package name + version with `aapt`, installed on emulator, ran 30-point deploy smoke test — all passed. The artifact the CI produces is the artifact users get. No "works on my machine."

### `ci-run.sh` Unified Test Runner
Wrapping all test commands in a single script solved the Claude Code permission friction permanently. One permission pattern (`bash ci-run.sh:*`) replaces 8+ fragile bash prefix patterns. The script also handles emulator auto-start, JAVA_HOME/ANDROID_HOME defaults, and subset selection (`unit`, `instrumented`, `relay`, `landing`).

### Package Lock Files Committed = Reproducible CI
Removing `package-lock.json` from `.gitignore` fixed `npm ci` in GitHub Actions and ensures dependency versions are pinned. This was a leftover from v0.2 when the relay didn't exist.

---

## What Didn't Go Well

### Firebase Test Lab IAM Was a Multi-Hour Yak Shave
The service account needed `roles/editor` to write to Test Lab's auto-provisioned storage bucket. We tried `storage.objectAdmin`, `firebase.admin`, `storage.admin` — none worked because the auto-provisioned bucket uses uniform bucket-level access that doesn't inherit project IAM. The actual fix: grant Editor + create a custom results bucket with `--results-bucket`. Firebase's docs recommend `roles/editor` for CI service accounts but this isn't obvious from the IAM console.

**Lesson:** For Firebase Test Lab CI, start with `roles/editor` and a custom `--results-bucket`. Don't try to find the minimal role — the auto-provisioned bucket permissions are opaque.

### Release APK Can't Run Debug Tests
First Firebase Test Lab run used the release APK — 18/19 passed, but `debugSimulateOffline_toggles` failed because `DebugReceiver.onReceive()` checks `IS_DEBUG` and returns early in release builds. Had to switch to debug APK for all Firebase Test Lab runs.

**Lesson:** Firebase Test Lab instrumented tests must use the debug APK, not the release APK. The test APK (`androidTest`) exercises debug features that are stripped from release builds.

### AGPL-3.0 License Text Hit Content Filter
Three background agents all failed to write the LICENSE file — the API content filter blocked the full AGPL-3.0 text. Fixed by downloading directly with `curl -sL https://www.gnu.org/licenses/agpl-3.0.txt`.

**Lesson:** For large standard legal texts, download from the canonical source rather than generating inline.

### Instrumented Tests Nearly Skipped — Again
The full verification run initially omitted instrumented tests. This was the second time. Fixed by updating MEMORY.md to mark instrumented tests as **MANDATORY** and adding them to `ci-run.sh` as a first-class suite.

**Lesson:** If a step keeps getting skipped, automate it or make it impossible to skip. `ci-run.sh` and Firebase Test Lab in CI both address this.

### Cloud Tool Results API Not Enabled by Default
The first CI run with Firebase Test Lab failed because `toolresults.googleapis.com` wasn't enabled on the GCP project. It's a separate API that must be explicitly enabled even when Firebase Test Lab is already configured.

**Lesson:** When setting up Firebase Test Lab, enable both `testing.googleapis.com` and `toolresults.googleapis.com`.

---

## Learnings

1. **Wrap test commands in scripts, not permission patterns.** Claude Code settings patterns like `Bash(./gradlew:*)` are fragile — they break when env var prefixes change. A single script with baked-in defaults is more stable and portable.

2. **Firebase Test Lab CI setup checklist:** (a) Blaze plan, (b) service account with `roles/editor`, (c) enable `toolresults.googleapis.com` and `testing.googleapis.com`, (d) custom `--results-bucket`, (e) `--project` flag explicit, (f) use debug APK for tests.

3. **Test the CI artifact, not just the source.** Downloading the CI-built APK and running smoke tests locally caught the gap between "CI says green" and "the APK actually works." This should be part of every release verification.

4. **`package-lock.json` must be committed for CI.** `npm ci` requires it, `setup-node` cache requires it. Never gitignore lock files in projects with CI.

5. **Don't overstate issues without evidence.** When troubleshooting the Firebase Test Lab bucket permissions, claiming "known bug" without a documentation link eroded trust. Provide evidence or say "I'm not sure why."

6. **The sideload guide is essential, not optional.** Non-technical parents need step-by-step instructions for "install unknown apps" and APK transfer. Four methods cover the range: Downloader app (easiest), Send Files to TV, USB, ADB.

7. **Auto-updating `version.json` in `release.yml` eliminates sync errors.** The same workflow that publishes the APK also publishes the version manifest — impossible for them to be out of sync.

---

## By the Numbers

- **34 files** changed
- **12 spec changes** implemented
- **27 new tests** (25 TV unit + 2 relay)
- **587 total tests** (was 560)
- **7 commits** on the branch (1 implementation + 6 CI fixes)
- **$0.08/run** Firebase Test Lab cost
- **1 PR** (#1) — first PR in the repo's history
- **30/30** deploy smoke test on CI-built APK
- **1 haiku** — tradition holds
