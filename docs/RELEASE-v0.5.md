# ParentApproved.tv v0.5 "The Polish" — Release Notes

**Date:** February 18, 2026
**Milestone:** The app looks like a product, not a prototype.

---

## One accent color, one font, one brand

v0.4 proved the product works — parents can manage playlists from anywhere. But it looked like three different apps stitched together: navy backgrounds, coral accents on the TV, teal on the dashboard, system fonts everywhere. v0.5 unifies the visual identity. Forest green (#22A559) is the only accent color. Roboto renders bold and readable on every TV. The warm dark palette feels like home on a living room screen.

```
Coral fades to green —
one checkmark across all screens,
finally, a brand.
```

---

## What's New

### Unified Color System
- **Kid screens** (HomeScreen, PlaybackScreen): warm dark palette — stone-900 background (#1C1917), stone-800 cards, forest green accent
- **Parent screens** (ConnectScreen, SettingsScreen): same warm dark palette — all TV screens now share the same visual language
- **Dashboard** (phone/laptop): light palette — off-white background, white cards, forest green accent, gray-600 secondary text
- Old navy (#1a1a2e), coral (#FF6B6B), and teal (#00d4aa) are gone everywhere

### Typography Overhaul
- TV app: system Roboto with enforced weights — Bold for headings, SemiBold for titles, Medium for body
- Dashboard: Nunito Sans from Google Fonts CDN (400, 500, 600, 700)
- bodySmall bumped to 14sp (was 12sp) — readable from the couch
- All text at Medium weight or above — nothing thin or light

### ConnectScreen Redesign
- Two-column layout: QR code left, branding + PIN right
- "ParentApproved" wordmark with green checkmark icon
- "One-time setup" label — reassuring, not scary
- 240dp QR code (was 200dp)
- PIN box with green border and monospace digits
- Charityware note in the right column
- Settings gear icon (Material Icons) in bottom-right corner

### SettingsScreen Refresh
- Two-column layout: settings left, charityware right
- All sections use warm dark palette with clear hierarchy
- Relay status color-coded: green (connected), amber (connecting), dim (disconnected)

### HomeScreen Polish
- Icon buttons: sync icon for Refresh, gear for Settings (was text buttons)
- "Connect Phone" button with phone icon
- All buttons use same surface color — no distracting highlight
- Green focus ring on video cards when navigated via D-pad

### VideoCard Improvements
- Corner radius: 12dp (was 8dp) — softer, friendlier
- Green focus ring border on D-pad focus
- Duration badge: semi-transparent surface (was opaque black)

### Dashboard CSS Overhaul
- Light theme: #FAFAFA background, white cards, #E5E7EB borders
- Forest green (#22A559) for all buttons, badges, progress bars, stats
- PIN input and stat values use darker green (#15803D) for WCAG AA contrast
- Offline banner: warm amber (#FEF3C7) instead of alarming red
- Nunito Sans loaded from Google Fonts CDN
- Body font-weight: 500 (Medium) — no thin text

### Brand Assets
- App icon: green circle + white checkmark on warm dark background
- Adaptive icon (API 26+): white checkmark foreground, green background
- Banner: warm dark background with centered green circle
- Favicon: green circle + white checkmark SVG (relay dashboard)

---

## Bug Fixes

- **Favicon missing on relay dashboard** — route and content type weren't configured. Added SVG route to relay Worker and Ktor server.
- **ConnectScreen overflow** — single-column layout didn't fit on TV. Redesigned as two-column.
- **Green text on white unreadable** — #22A559 fails WCAG contrast on white. Added #15803D (green-700) for text-on-light contexts.
- **Gray text too faint** — #6B7280 (gray-500) unreadable at small sizes. Darkened to #4B5563 (gray-600).
- **Variable font rendered thin** — Nunito Sans variable TTF ignores Compose weight requests; all text rendered at 400 weight. Switched to system Roboto which properly respects FontWeight.

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| TV unit tests | 157 | `./gradlew testDebugUnitTest` |
| Relay tests | 139 | `cd relay && npx vitest run` |
| TV instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| **Total verified** | **315** | |

All tests pass on both emulator (TV_API34) and real hardware (Mi Box 4).

---

## Deferred

- **"NEW" badge on videos**: Requires `addedAt` field in VideoItem + Room migration. Deferred to v0.6.
- **Thumbnail shimmer**: Compose shimmer without a library is more complex than justified. Deferred.
- **Nunito Sans on TV**: Variable TTF doesn't work with Compose FontWeight. Would need individual static weight TTF files (4 files, ~400KB each). System Roboto is readable and consistent. Revisit if design review warrants it.

---

## Verified Hardware
- **Emulator:** TV_API34 (Android 14, arm64)
- **Real device:** Xiaomi Mi Box 4 (MIBOX4, Android 9, API 28) on Panasonic TV
- **Dashboard:** Chrome on Android phone, Chrome on laptop
