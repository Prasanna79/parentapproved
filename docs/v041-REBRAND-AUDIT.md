# KidsWatch → ParentApproved.tv — Rebrand Audit

**Version:** v0.41
**Purpose:** Rename all user-visible "KidsWatch" to "ParentApproved" before taking screenshots and going live.

---

## Phase 1: Must Change (user-visible, 13 areas)

### TV App — Android System

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 1 | `tv-app/app/src/main/res/values/strings.xml` | 2 | `<string name="app_name">KidsWatch</string>` | `ParentApproved` |
| 2 | `tv-app/app/src/main/AndroidManifest.xml` | 18 | `android:label="KidsWatch"` | `android:label="ParentApproved"` |

### TV App — Compose UI

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 3 | `tv-app/.../ui/screens/HomeScreen.kt` | 70 | `Text("KidsWatch", ...)` | `Text("ParentApproved", ...)` |
| 4 | `tv-app/.../ui/screens/ConnectScreen.kt` | 194 | `"KidsWatch is free, forever..."` | `"ParentApproved.tv is free, forever..."` |

### Parent Dashboard (served from TV)

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 5 | `tv-app/app/src/main/assets/index.html` | 6 | `<title>KidsWatch Dashboard</title>` | `ParentApproved Dashboard` |
| 5 | `tv-app/app/src/main/assets/index.html` | 12 | `<h1>KidsWatch</h1>` (auth) | `ParentApproved` |
| 5 | `tv-app/app/src/main/assets/index.html` | 23 | `<h1>KidsWatch</h1>` (dashboard) | `ParentApproved` |

### Relay Dashboard (Cloudflare Worker)

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 6 | `relay/assets/index.html` | 6 | `<title>KidsWatch Dashboard</title>` | `ParentApproved Dashboard` |
| 6 | `relay/assets/index.html` | 16 | `"Please update KidsWatch on your TV."` | `"Please update ParentApproved on your TV."` |
| 6 | `relay/assets/index.html` | 20 | `<h1>KidsWatch</h1>` (auth) | `ParentApproved` |
| 6 | `relay/assets/index.html` | 31 | `<h1>KidsWatch</h1>` (dashboard) | `ParentApproved` |

### Server Fallback HTML

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 7 | `relay/src/index.ts` | 135 | `<title>KidsWatch</title>...<p>KidsWatch Relay...</p>` | `ParentApproved` |
| 8 | `tv-app/.../server/DashboardRoutes.kt` | 15 | `<h1>KidsWatch Dashboard</h1>` | `ParentApproved Dashboard` |

### Tests (will fail after rebrand)

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 9 | `tv-app/scripts/ui-test.sh` | 133 | `assert_ui_text "..." "KidsWatch"` | `"ParentApproved"` |
| 9 | `tv-app/scripts/ui-test.sh` | 192 | `assert_ui_text "..." "KidsWatch"` | `"ParentApproved"` |
| 9 | `tv-app/scripts/ui-test.sh` | 312 | `grep -q "KidsWatch Dashboard"` | `"ParentApproved Dashboard"` |

### Relay Deployment Config

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 10 | `relay/wrangler.toml` | 1, 24 | `name = "kidswatch-relay"` | `parentapproved-relay` |
| 11 | `relay/package.json` | 2 | `"name": "kidswatch-relay"` | `parentapproved-relay` |

### Marketing Landing Page (GitHub URLs)

| # | File | Lines | Current | Change to |
|---|------|-------|---------|-----------|
| 12 | `marketing/landing-page/index.html` | 48, 498, 597, 614, 615, 616, 902, 1010 | `github.com/user/kidswatch` | Real GitHub repo URL (TBD) |

### Public README

| # | File | Line(s) | Current | Change to |
|---|------|---------|---------|-----------|
| 13 | `README.md` | 1, 15, 22 | `# KidsWatch`, `KidsWatch gives parents...` | `# ParentApproved.tv`, update description |

---

## Phase 2: Update Later (docs, not blocking)

These are specs, release notes, and internal docs. They're historical records — update as time allows.

- `CLAUDE.md` — developer reference
- `docs/V02-*.md`, `docs/V03-*.md`, `docs/V04-*.md` — version specs
- `docs/RELEASE-*.md`, `docs/RETRO-*.md` — release notes/retros
- `docs/napkin-*.md` — brainstorm notes
- `docs/landing-page-spec.md`, `docs/competitive-analysis.md`
- `docs/kidswatch-prd-prompt.md`
- `marketing/mom-psychographics-research.md`
- Shell script banner/comments in `tv-app/scripts/`
- Relay code comments in `relay/src/`

---

## Phase 3: DO NOT Change (keep `com.kidswatch.tv`)

Changing these would break existing installs or require a migration. Keep as-is.

- **Package name:** `com.kidswatch.tv` (all Kotlin files, build.gradle.kts)
- **Application ID:** `com.kidswatch.tv` (build.gradle.kts)
- **Class names:** `KidsWatchApp`, `KidsWatchServer`, `KidsWatchTVTheme`
- **SharedPreferences:** `kidswatch_sessions`, `kidswatch_relay`
- **Room database:** `kidswatch_cache`
- **Logcat tags:** `KidsWatch-Intent`, `KidsWatch`
- **Debug intent actions:** `com.kidswatch.tv.DEBUG_*`
- **Gradle root project:** `KidsWatchTV`
- **Feasibility test app:** entire `android-tv-test/` directory

---

## Also in v0.41: UI Skin & Color Improvements

(TBD — add color/theme changes here as they're decided)

---

## Checklist

- [ ] Phase 1 rebrand (13 areas)
- [ ] Build & run unit tests
- [ ] Build & install on Mi Box
- [ ] Take screenshots for landing page (TV home, connect, dashboard)
- [ ] Update landing page with real screenshots
- [ ] Update landing page GitHub URLs to real repo
- [ ] Phase 2 doc updates (optional, low priority)
