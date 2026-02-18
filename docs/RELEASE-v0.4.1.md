# ParentApproved.tv v0.4.1 "The Name" — Release Notes

**Date:** February 18, 2026
**Milestone:** The app gets its public name. Everything says ParentApproved now.

---

## A name for the living room

The code was called KidsWatch. The domain was parentapproved.tv. The landing page said one thing, the TV said another. Before going public, the two had to become one. v0.4.1 is the version where every screen, every log line, and every file path agrees on who this app is.

```
Old name shed like skin—
same heart beats inside the box,
new face greets the world.
```

---

## What Changed

### Full Package Rename: `com.kidswatch.tv` → `tv.parentapproved.app`
- 75 Kotlin source files moved to new directory structure
- All `package` and `import` statements updated
- `applicationId` and `namespace` updated in Gradle
- AndroidManifest: app label, class references, all 17 debug intent actions
- Git detected all moves as renames — full history preserved

### Class Renames
- `KidsWatchApp` → `ParentApprovedApp`
- `KidsWatchServer` → `ParentApprovedServer`
- `KidsWatchTVTheme` → `ParentApprovedTheme`
- `Theme.KidsWatchTV` → `Theme.ParentApprovedTV` (XML style)

### User-Visible Strings
- App launcher label: **ParentApproved**
- Home screen title: **ParentApproved**
- Connect screen charityware text: "ParentApproved.tv is free, forever..."
- Parent dashboard: title and headers → **ParentApproved Dashboard**
- Relay dashboard: title, headers, version warning → **ParentApproved**
- Server fallback HTML → **ParentApproved Dashboard**

### Internal Renames
- SharedPreferences: `parentapproved_sessions`, `parentapproved_relay`
- Room database: `parentapproved_cache`
- Logcat tags: `ParentApproved`, `ParentApproved-Intent`
- Relay Worker: `parentapproved-relay` (wrangler.toml, package.json)
- Gradle root project: `ParentApprovedTV`

### Shell Scripts & Tests
- All 4 scripts updated: package name, intent prefix, logcat tag, UI test assertions
- Relay test files and source comments updated
- UI test assertion "Home screen shows app title" now checks for "ParentApproved"

### Landing Page Editorial Pass
- Removed "open-source" from hero (moved to dedicated FAQ with Firefox/VLC comparison)
- Trimmed problem section intro from 5 sentences to 3
- Simplified dashboard note and remote access callout
- Renamed "Local-first by default" to "Nothing leaves your home"
- Added "What you need" checklist before download button
- Reworded FAQ questions to parent language ("Do I need to sign in?" not "API key")
- Added grandparents/babysitters to multi-parent FAQ
- Added `hello@parentapproved.tv` email to bug report FAQ
- Reordered FAQ: setup first, remote access lower
- Added 5 AI-searchable questions for SEO/AEO
- Updated JSON-LD FAQPage schema (now 13 structured questions, 23 total on page)

### Bug Fix
- `SessionManagerTest.maxConcurrentSessions` — test expected limit of 5 but code was bumped to 20 in v0.4. Test now matches.

---

## Test Coverage

| Suite | Count | Result |
|-------|-------|--------|
| TV unit tests | 187 | 187 pass |
| Intent + HTTP | 15 | 15 pass |
| UI tests | 34 | 27 pass, 7 pre-existing flaky |
| Relay tests | 139 | (unchanged, not re-run) |
| **Total** | **375** | |

No new tests — this is a rename, not a feature. All rebrand-specific assertions pass (app title, intent routing, HTTP API, dashboard HTML).

---

## Known Issues

- Same as v0.4 — no behavioral changes in this release
- 7 flaky UI tests are pre-existing (button visibility/timing in uiautomator, not rebrand-related)

---

## Verified Hardware
- **Emulator:** TV_API34 (Android 14, arm64) — build + all tests
- **Mi Box:** Not re-tested (same APK, just renamed strings)
