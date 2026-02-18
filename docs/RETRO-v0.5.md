# ParentApproved.tv v0.5 Retrospective

**Date:** February 18, 2026
**Scope:** Visual rebrand — colors, typography, layout, icons, dashboard CSS

---

## What Went Well

### The Dark Palette Won
The napkin spec proposed a dual palette: warm dark for kid screens, light for parent screens on TV. After implementing it, the light ConnectScreen and SettingsScreen looked terrible on a real TV — washed out, low contrast, uncomfortable in a living room. We unified everything to the warm dark palette. The dashboard (phone/laptop) stays light because phones are different surfaces. The lesson: TV UX conventions exist for a reason. Netflix, YouTube, Disney+ are all dark. Don't fight the medium.

### CSS Was a Clean Swap
Both dashboard CSS files (relay + local) were nearly identical before and after. The color swap from navy/teal to white/green was mechanical — find old hex, replace with new hex. No structural changes to the HTML or JS. The dashboard worked correctly on first deploy.

### Material Icons Extended Was Worth the Dependency
Adding `material-icons-extended` gave us proper vector icons for the settings gear, sync button, and phone icon. Replaced Unicode emoji characters and text labels. The APK size increase is negligible (tree-shaken), and the icons render crisply at any resolution.

### Iterating on Real Hardware Caught 5 Visual Bugs
Every visual issue — green-on-white contrast, gray too faint, font too thin, layout overflow, light-on-TV — was caught by looking at the actual TV screen. None of these would have been caught by unit tests or emulator screenshots.

---

## What Didn't Go Well

### Variable Fonts Don't Work in Compose
The biggest surprise. We downloaded Nunito Sans as a variable TTF, placed it in `res/font/`, and defined a `FontFamily` with explicit `Font(R.font.nunito_sans, weight = FontWeight.Bold)` entries. Compose ignored every weight request and rendered everything at the font's default weight (thin/regular). The text was nearly invisible on a TV screen.

**Root cause:** Android's Compose `Font()` resource loader doesn't send weight axis values to variable fonts. It treats the single TTF as a single weight. To get multiple weights, you need individual static TTF files (one per weight) — which defeats the purpose of a variable font.

**Fix:** Switched to `FontFamily.Default` (system Roboto), which has proper static weight files baked into Android. All weight specifications (Bold, SemiBold, Medium) now render correctly.

**Lesson:** Don't use variable fonts in Android Compose. Use static weight files, or use the system font.

### Three Rounds of "Still Unreadable"
The readability feedback loop was painful:
1. First attempt: green (#22A559) text on white — fails WCAG contrast. Fixed with darker green (#15803D).
2. Second attempt: gray (#6B7280) on white at 12sp — too faint. Fixed by darkening to #4B5563 and bumping to 14sp.
3. Third attempt: light background on TV — everything washed out. Fixed by switching to dark palette everywhere.
4. Fourth attempt: Nunito Sans variable font — all text thin. Fixed by switching to Roboto.

Each round required a full build → install → look → feedback → fix cycle. Four iterations to get readable text.

**Lesson:** Visual changes need a tighter feedback loop. Consider a "typography test screen" that shows all text styles at once — one glance tells you if weights are working.

### The Light Parent Palette Was a Dead End
We spent implementation time building a light ParentColorScheme, wiring it into ConnectScreen and SettingsScreen, then ripping it all out when the user said it looked terrible on TV. The Parent palette colors still exist in Color.kt (used by the web dashboard CSS) but aren't used by any TV screen.

**Lesson:** Prototype visual changes on the target device BEFORE implementing across all screens. A 2-minute mockup on the TV would have killed the light-palette idea before any code was written.

### Favicon Required Two Separate Fixes
The favicon SVG was created but didn't appear in the browser. Two separate routing gaps:
1. Relay Worker (`index.ts`): no route for `/favicon.svg` and no content type mapping
2. Ktor server (`DashboardRoutes.kt`): no `.svg` → `image/svg+xml` content type mapping

**Lesson:** When adding a new static asset type, check ALL serving paths. This app serves assets from both Cloudflare Workers and an embedded Ktor server.

---

## Learnings

1. **Variable fonts + Compose = broken.** Android Compose doesn't send weight axis values to variable TTFs. Use static weight files or the system font. This is not documented anywhere obvious.

2. **TV screens demand dark themes and bold weights.** Light backgrounds are hostile on a 55-inch screen in a dim room. Thin fonts (< Medium/500 weight) are invisible from couch distance. Every text style should be Medium (500) or above.

3. **WCAG contrast matters more on TV.** The standard was designed for desktop monitors at arm's length. TVs are viewed from 6-10 feet. Colors that barely pass WCAG AA at a desk will fail the squint test on a TV. Use darker variants for text colors.

4. **One accent color is enough.** Coral for TV, teal for dashboard, green for landing page — three accent colors looked like three products. One green (#22A559) everywhere instantly created brand coherence. Simpler is better.

5. **CSS color swaps are safe.** Changing every color in the dashboard CSS (navy→white, teal→green, dark surface→white cards) didn't break a single test. The relay's 139 tests and the dashboard's 20 tests all passed. CSS is the safest layer to refactor.

6. **Build on hardware, not in your head.** The dual-palette idea (dark for kids, light for parents) made sense in a design spec. It failed the moment it hit a real TV. The variable font looked great in the IDE. It failed the moment it rendered on screen. Every visual decision should be validated on the target device.

---

## By the Numbers

- **19 files changed**, 768 insertions, 437 deletions
- **4 visual iteration rounds** (green contrast, gray contrast, light-on-TV, thin font)
- **5 visual bugs** caught only by looking at real hardware
- **0 test regressions** — 157 unit + 139 relay + 19 instrumented, all green
- **1 new dependency** (material-icons-extended)
- **1 font abandoned** (Nunito Sans variable TTF — Compose can't use it)
- **1 dead-end palette** (light parent palette on TV — looked terrible)
- **1 haiku** — tradition holds

---

## v0.6 Priorities (from this retro)

| Priority | Item | Why |
|----------|------|-----|
| P0 | Video titles in Now Playing + Recent | Dashboard shows raw video IDs — carried from v0.4 |
| P1 | "NEW" badge on recently added videos | Requires Room migration for `addedAt` timestamp |
| P1 | Thumbnail shimmer loading placeholder | Blank space while Coil loads is jarring |
| P2 | Nunito Sans static weight files | If design review wants a custom font, need 4 static TTFs |
| P2 | Typography test screen (debug) | Would have caught the font weight bug in 30 seconds |
| P2 | Focus order on HomeScreen | D-pad Down from Refresh skips to 3rd video (carried from v0.4) |
