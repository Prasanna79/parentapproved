# ParentApproved.tv v0.6.1 "Dashboard Polish" — Release Notes

**Date:** February 18, 2026
**Milestone:** The dashboard parents actually use — now with thumbnails, saner polling, and a gentle ask.

---

## Now Playing gets a face

The Now Playing card was text-only: a title (sometimes just a video ID slug) and a progress bar. Now it shows the YouTube thumbnail — 320x180, no API key, loads instantly. You can see what your kid is watching at a glance without squinting at a title.

```
A thumbnail appears —
the kid watches, the parent knows,
thirty seconds pass.
```

---

## What's New

### Now Playing Thumbnail
- YouTube thumbnail image above the title in the Now Playing card
- URL pattern `img.youtube.com/vi/{videoId}/mqdefault.jpg` — works for all public videos, no API key
- Thumbnail clears when playback stops (no stale image on the card)

### Polling Rates: Battery-Friendly
- **Playing:** 30s (was 5s) — a parent dashboard doesn't need real-time updates
- **Idle:** 120s (was 30s) — when nothing is playing, check twice a minute is plenty
- Initial load also starts at 120s instead of 30s
- Phone battery will thank you on long viewing sessions

### Charityware Footer
- "ParentApproved.tv is free, forever." — visible after Recent Activity
- Links to [mettavipassana.org/donate](https://mettavipassana.org/donate) for India, encourages local Buddhist charity worldwide
- Subdued styling — there when you scroll, never in the way

### "Add to Home Screen" Banner
- Dismissible banner on mobile browsers: "Add ParentApproved to your home screen for quick access"
- Chrome Android: captures `beforeinstallprompt`, [Add] button triggers native install prompt
- iOS: shows "Tap Share then 'Add to Home Screen'" (no native prompt available)
- Hidden on desktop, hidden if already in standalone mode, dismiss persists in localStorage
- Relay version uses per-TV dismiss key (`kw_homescreen_dismissed_{tvId}`)

---

## Files Changed

All changes are dashboard-only (HTML/CSS/JS). No Kotlin changes.

| File | Changes |
|------|---------|
| `tv-app/app/src/main/assets/index.html` | Thumbnail img, homescreen banner, charityware footer |
| `tv-app/app/src/main/assets/app.js` | Thumbnail in loadStatus, polling rates, homescreen JS |
| `tv-app/app/src/main/assets/style.css` | `.np-thumbnail`, `.charityware`, `.homescreen-banner` |
| `relay/assets/index.html` | Same HTML changes |
| `relay/assets/app.js` | Same JS changes |
| `relay/assets/style.css` | Same CSS changes |
| `tv-app/app/build.gradle.kts` | versionName 0.6.0 → 0.6.1, versionCode 7 → 8 |

---

## Friction Log

Added entry for **video-metadata-not-flowing** — 3rd occurrence (v0.4 Recent Activity, v0.6 playlist list, v0.6.1 Now Playing). Per the 3-strike rule, `WatchableContent` domain object is now a candidate for implementation. See `docs/friction-log.md`.

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| TV unit tests | 191 | `./gradlew testDebugUnitTest` |
| Relay tests | 139 | `cd relay && npx vitest run` |
| TV instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| **Total verified** | **349** | |

No new tests — dashboard is manually tested. All existing tests pass.

---

## Verification Checklist

- [x] `assembleDebug` builds
- [x] Unit tests pass
- [x] Relay tests pass (139/139)
- [ ] Install on emulator, play a video, verify thumbnail appears
- [ ] Verify polling slows to ~30s during playback (Network tab)
- [ ] Stop playback, verify polling slows to ~120s
- [ ] Scroll to charityware footer, verify link opens in new tab
- [ ] Mobile Chrome: homescreen banner appears, dismiss persists
- [ ] Diff local vs relay assets — structure matches
