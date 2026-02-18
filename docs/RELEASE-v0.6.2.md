# ParentApproved.tv v0.6.2 "The Brand" — Release Notes

**Date:** February 18, 2026
**Milestone:** Logo, favicon, and consistent branding across every surface.

```
Green check, small mark —
every screen now knows its name.
Parents find their way.
```

---

## What's New

### Dashboard Branding
- Inline SVG logo (green checkmark) + "ParentApproved.tv" in both auth screen and dashboard header
- Favicon added to local dashboard (was relay-only) — browser tabs now show the green checkmark
- Page title changed from "ParentApproved Dashboard" to "ParentApproved.tv"

### Now Playing Title Fix
- Fixed race condition where Now Playing showed video ID slug instead of title
- Root cause: playlist cache hadn't loaded when `extractAndPlay` fired on first video
- Fix: `PlayEventRecorder.updateTitle()` updates title from NewPipe extractor after `fetchPage()`
- 3 new unit tests covering the fix

### Marketing Site
- Site header with logo + "ParentApproved.tv" in Nunito Sans (matches dashboard font)
- Favicon added
- Logo sized to 22px — subtle, not shouty

---

## Files Changed

| File | Changes |
|------|---------|
| `tv-app/app/src/main/assets/index.html` | Logo SVG in h1, favicon link, title |
| `tv-app/app/src/main/assets/style.css` | `.logo` styles |
| `tv-app/app/src/main/assets/favicon.svg` | New — green checkmark favicon |
| `relay/assets/index.html` | Logo SVG in h1, title |
| `relay/assets/style.css` | `.logo` styles |
| `tv-app/app/src/main/java/.../PlayEventRecorder.kt` | `updateTitle()` method |
| `tv-app/app/src/main/java/.../PlaybackScreen.kt` | Call `updateTitle()` after extraction |
| `tv-app/app/src/test/.../PlayEventRecorderTest.kt` | 2 new tests |
| `tv-app/app/src/test/.../StatusRoutesTest.kt` | 1 new test |
| `marketing/landing-page/index.html` | Site header, favicon, Nunito Sans font |
| `marketing/landing-page/style.css` | `.site-header`, `.site-brand`, `.site-logo` |
| `marketing/landing-page/favicon.svg` | New |
| `tv-app/app/build.gradle.kts` | versionName 0.6.1 → 0.6.2, versionCode 8 → 9 |

---

## Test Coverage

All existing tests pass + 3 new tests for `updateTitle`.
