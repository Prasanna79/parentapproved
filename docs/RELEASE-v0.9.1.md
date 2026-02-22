# ParentApproved.tv v0.9.1 — Release Notes

**Date:** February 22, 2026
**Milestone:** Landing page polish for friends-and-family launch.

```
the sign now reads "open" —
no more "coming soon" it says,
step in, have a look.
```

---

## What's New

v0.9.1 is a **landing page update only**. No app changes, no relay changes, no new tests.

### Landing Page Updates

- **Hero CTA**: "Get Notified at Launch" → "Download Free" — the app is available now
- **Beta banner**: "Coming soon" → "Now available"
- **FAQ fix**: Removed outdated claim that "daily time limits aren't built yet" (shipped in v0.7)
- **Privacy policy**: Added link to `privacy.html` in footer, replacing "No analytics tracking" bullet

---

## Files Changed (1 file)

| Category | Files |
|----------|-------|
| **Landing page** | `marketing/landing-page/index.html` |

---

## Test Coverage

No change from v0.9 — 587 total tests. All passing.

---

## Deployment

Landing page only: `cd marketing/landing-page && npx wrangler pages deploy . --project-name parentapproved-tv`

No app or relay changes — no APK or relay deploy needed.
