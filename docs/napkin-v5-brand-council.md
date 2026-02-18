# v0.5 Brand & UX Council — Napkin Notes

**Date:** 2026-02-18
**Context:** The TV app works. The relay works. 250 tests pass. But the product looks like a developer built it for themselves (because one did). Before shipping to real parents, the three surfaces — TV app, phone dashboard, and landing page — need to feel like the same product. This is that conversation.

**Participants:**
- **Priya** — Parent persona (the overwhelmed curator, from STYLE-GUIDE.md)
- **Maya** — Product Manager
- **Dev** — Dev Lead (the founder who built v0.1-v0.4)
- **Suki** — UX Lead
- **Raj** — Security Expert

---

## Round 1: The Problem Statement

**Maya:** Before we touch a single pixel, let me put three screenshots on the table. Here's the TV app — dark navy with a coral pink accent. Here's the phone dashboard — same dark navy but with a teal green accent. And here's the style guide for the landing page — white background, forest green buttons, warm and professional. These look like three different products.

**Suki:** It's worse than that. The PRD says the kid UI should be "light, playful, warm — soft gradient, warm cream #FFF8F0, soft peach, Nunito font, bouncing animals, stars and clouds." The actual TV app is a dark developer theme with system fonts and zero decoration. Zero. The banner.xml is literally a coral circle on a navy rectangle.

**Dev:** Fair. The dark theme was a pragmatic choice — I was building on an actual TV in my living room, and dark backgrounds look good on screens in dim rooms. The coral-vs-teal thing is embarrassing — I picked teal for the dashboard because I wanted it to feel different from the TV, but that was a mistake. They should feel connected, not different.

**Priya:** Can I be honest? I don't care about the dark-vs-light debate. When I scan that QR code on my TV, I want the thing that opens on my phone to feel like it belongs to the thing I just scanned. Right now it doesn't. The TV shows a coral PIN, my phone shows a teal input box. I'd wonder if I'm on the right page.

**Maya:** That's the whole problem in one sentence. Let's solve it.

---

## Round 2: Two Audiences, Two Surfaces

**Suki:** OK, here's the fundamental tension. The TV app has two audiences viewing the same screen:

1. **Kids** — who see the HomeScreen (video grid) and PlaybackScreen 99% of the time
2. **Parents** — who see the ConnectScreen (QR + PIN) during setup, and occasionally the SettingsScreen

And then the phone dashboard is 100% parents. And the landing page is 100% parents (prospective ones).

**Maya:** So the question is: does the TV app need to look like the kid UI from the PRD (playful, warm, light), or like the parent dashboard (clean, professional)?

**Suki:** Both. Different screens, different jobs. The HomeScreen and PlaybackScreen are kid-facing. They should be warm, inviting, fun. The ConnectScreen and SettingsScreen are parent-facing. They should feel connected to the dashboard — same colors, same typography, same energy.

**Dev:** That's actually clean from an implementation standpoint. The nav graph already separates these screens. I can apply different color palettes per screen without changing architecture.

**Raj:** Quick security note — the ConnectScreen shows the PIN and the QR code which encodes the tv-secret. The visual treatment of that screen should convey "this is important, this is secure" without looking scary. The style guide says "no alarm red." Coral reads as alarm-ish on a phone.

**Priya:** When I see the ConnectScreen with a big QR code and a PIN, I want to feel "oh, this is straightforward, I can do this." Not "oh no, security setup." Keep it simple and clear.

---

## Round 3: The Name Question

**Maya:** We need to address the elephant. The code says "KidsWatch." The marketing style guide says "ParentApproved.tv." Which is it?

**Dev:** KidsWatch was a working title. ParentApproved.tv is the public name. But I haven't updated the app because I wasn't sure we'd stick with it.

**Maya:** Let's decide now. ParentApproved.tv has a clearer value proposition — it says what it does. "KidsWatch" could be a surveillance app. "ParentApproved" says "you approved this, it's safe."

**Priya:** "ParentApproved" makes me feel good about myself. "KidsWatch" makes me feel like I'm monitoring them. One empowers me, the other makes me a warden.

**Suki:** And "ParentApproved.tv" has the domain baked into the name. It's the URL and the brand. That's elegant.

**Dev:** OK, but in the kid-facing UI on the TV, do we show the brand at all? The style guide says "no algorithm, no YouTube chrome." Should we also have "no ParentApproved.tv chrome"? The kid doesn't need to know the app's name. They just see their videos.

**Maya:** Minimal branding on the kid screens. A small wordmark in the corner of HomeScreen, maybe. The ConnectScreen (parent-facing) should show the full brand clearly — that's the moment the parent is first meeting the product.

**Suki:** Agreed. The kid screens should feel like the kid's space, not a product. The parent screens should feel like a product the parent trusts.

**Dev:** I'll keep the package name `com.kidswatch.tv` to avoid a migration headache. The user-facing name changes to ParentApproved. The `app_name` in strings.xml changes. The launcher label changes.

**Raj:** No security implications from a rename. The tv-id, tv-secret, relay protocol — none of that cares about the display name.

---

## Round 4: Color System

**Suki:** Let me propose a unified color system that works across all three surfaces.

**Foundation colors (shared everywhere):**
- **Background (parent surfaces):** #FAFAFA (off-white) — matches style guide
- **Background (kid surfaces on TV):** #FFF8F0 (warm cream) — from PRD, warmer for a living room
- **Text primary:** #1A1A1A (near-black) — matches style guide
- **Text secondary:** #6B7280 (warm gray) — matches style guide
- **Accent / CTA:** #22A559 (forest green) — matches style guide. Trust, not alarm.

**But wait** — dark mode on a TV?

**Dev:** Real talk. I've been running this on a Mi Box in my living room for months. The dark theme works because the TV is often in a dim room. A bright white background on a 55-inch screen at night is a flashlight in the face.

**Priya:** I watch TV at night. A white screen would be brutal. Can we do the warm cream but darker? Like a warm dark mode?

**Suki:** Good instinct. The PRD says "light, playful, warm." But TVs are different from phones. TV UX conventions are almost universally dark for a reason — dark rooms, large screens, reduced eye strain. YouTube, Netflix, Disney+ — all dark. Going light is a bold choice. Going white is hostile.

**Maya:** So what do we actually do?

**Suki:** Here's my proposal. Two palettes:

**TV Kid Palette (HomeScreen, PlaybackScreen) — warm dark:**
| Token | Value | Notes |
|-------|-------|-------|
| Background | #1C1917 | Warm near-black (stone-900) — NOT blue-navy, but earthy warm |
| Surface | #292524 | Warm dark (stone-800) — cards, elevated elements |
| Accent | #22A559 | Forest green — same as CTA everywhere |
| Text | #F5F5F4 | Warm white (stone-100) |
| Text dim | #A8A29E | Warm gray (stone-400) |
| Focus ring | #22A559 at 60% | Green glow on focused card |
| Playlist headers | Accent color emoji + warm white text |
| Duration badge | #292524 at 85% alpha |
| "NEW" badge | #FDE68A (warm yellow) | For videos added < 24h ago |

**Parent Palette (ConnectScreen, SettingsScreen, Dashboard, Landing Page):**
| Token | Value | Notes |
|-------|-------|-------|
| Background | #FAFAFA | Off-white — style guide |
| Surface | #FFFFFF | Pure white cards |
| Accent / CTA | #22A559 | Forest green — trust, not alarm |
| Text | #1A1A1A | Near-black |
| Text secondary | #6B7280 | Warm gray |
| Border | #E5E7EB | Light gray |
| Error / destructive | #DC2626 | Red-600, used sparingly |
| Warning | #D97706 | Amber-600 |
| Success | #16A34A | Green-600 |

**Dev:** So the TV app has TWO themes active at the same time, switching based on which screen you're on?

**Suki:** Yes. It's one theme with two sub-palettes. The nav graph already cleanly separates kid screens from parent screens. Each screen wraps its content in a `Surface` with the right background.

**Dev:** Easy to implement. I'll make `KidThemeColors` and `ParentThemeColors` objects in the theme file. Each screen picks the right one.

**Raj:** On the dashboard — the teal (#00d4aa) goes away entirely? Good. It was never in the style guide. Having one accent color (#22A559 forest green) across every surface means visual coherence AND it means the "Connect" button on the TV, the CTA on the landing page, and the action buttons on the dashboard all read as the same brand.

**Maya:** I love that the accent is green everywhere. Green = go, green = approved. It's literally in the brand name. Parent*Approved*.

**Priya:** Green feels calm and trustworthy to me. The coral felt... intense. Teal felt clinical. Green feels like "everything is fine." Which is what I want to feel when I'm managing my kid's TV.

---

## Round 5: Typography

**Suki:** The PRD says Nunito or Quicksand — rounded, playful. The style guide says Inter or DM Sans — clean, professional. Again, two audiences.

**Maya:** Do we bundle two fonts in the APK?

**Dev:** Font files add ~200-400KB each. Two fonts = under 1MB. Not a problem. But complexity-wise, do we need it? What if we pick ONE font that spans both moods?

**Suki:** Let me make the case for **one font: Nunito Sans.** It's the sans-serif sibling of Nunito. It has the rounded terminals that feel friendly (like Nunito) but the proportions of a professional sans-serif (like Inter). It works at 12sp on a settings screen AND at 28sp as a playlist header on a TV. And it's on Google Fonts — free, open source.

**Dev:** I like it. One font, one import, one weight file (or variable font). Nunito Sans has weights from 200 to 900. We'd use:
- Regular (400) — body text
- Medium (500) — labels, buttons
- SemiBold (600) — titles
- Bold (700) — headings

**Priya:** Honestly? I don't notice fonts. I'd notice if it looked jagged or hard to read. Just make it readable on my phone and readable on the TV from the couch.

**Suki:** Noted. Minimum 14sp on TV (which is fine at 10-foot viewing distance), minimum 16px on phone dashboard. Body text on the landing page stays at 18px per the style guide.

**Maya:** One font. Nunito Sans. Let's move on.

**Dev:** For the TV, I'll bundle `NunitoSans-VariableFont.ttf` in `res/font/`. For the dashboard, I'll load it from Google Fonts CDN. For the landing page, same CDN. Everywhere gets the same typeface.

---

## Round 6: The Logo & Wordmark

**Suki:** We have no logo. The banner.xml is a coral circle on navy. The launcher is a solid blue rectangle. We need:

1. **App icon** (launcher on Android TV home screen) — 320x180 banner + adaptive icon
2. **Wordmark** (for ConnectScreen, dashboard header, landing page)
3. **Favicon** (for the dashboard in the phone browser tab)

**Maya:** Keep it dead simple. We're not a Fortune 500 company. We're one person building something for their kids.

**Suki:** Proposal: the wordmark is just the text **ParentApproved** in Nunito Sans Bold, with a small green checkmark after it. That's it. The checkmark is the logo mark — it can stand alone as the favicon and app icon.

Like: **ParentApproved** followed by a green circle with a white check.

The icon for the Android TV launcher: warm dark background (#1C1917), centered green checkmark. Simple, recognizable at TV distance, distinct from every other app on the Mi Box home screen.

**Dev:** I can render the checkmark as a vector drawable. No need for a designer. It's a circle (#22A559) with a white (#F5F5F4) checkmark path inside. Maybe 4-5 lines of XML.

**Priya:** A checkmark makes sense to me. "Approved." I get it instantly.

**Raj:** Just make sure the checkmark doesn't look like Google's verified badge or a banking app's "secure" icon. Keep it soft — rounded corners on the check, not sharp. We're not verifying identity, we're saying "you approved this."

**Suki:** Good call. Rounded check, slightly thicker stroke (3dp), sits inside a circle with some padding. Friendly, not official.

**Maya:** What about the ".tv" in the name? ParentApproved.tv?

**Suki:** In the wordmark, it's just "ParentApproved" — the ".tv" only appears in the URL. On the landing page, the domain IS the brand: parentapproved.tv. But in the app and dashboard, just "ParentApproved."

**Dev:** The `app_name` string becomes "ParentApproved". The TV banner shows the wordmark. The dashboard `<h1>` shows the wordmark. The landing page shows it with ".tv" as a domain reference.

---

## Round 7: TV Kid UI Polish — How Far?

**Maya:** The PRD describes a very polished kid UI — stars, clouds, rainbows, bouncing animal loading animations, random card tilts, spring scale animations. How much of that do we actually build in v0.5?

**Dev:** I'd push back on the decorative elements hard. Stars, clouds, rainbows — that's a lot of Compose canvas drawing or SVG assets for something that doesn't improve usability. The kid doesn't care about corner stars. They care about finding their video and pressing OK.

**Suki:** I agree partially. The decorative elements in the PRD were aspirational — "someday" stuff. But some of the polish matters:

**Do in v0.5 (high impact, low effort):**
- Focus animation improvement: scale 1.05x AND green focus ring (currently just scale, no ring)
- Card corner radius: bump from 8dp to 12dp (feels softer)
- "NEW" badge on videos added < 24h (yellow sparkle dot, 4 lines of code)
- Playlist header: emoji prefix (already in the data model? if not, easy to add)
- Skeleton loading shimmer while thumbnails load (Coil supports placeholder)

**Defer (high effort, marginal impact):**
- Random card tilt (accessibility concern — motion sensitivity)
- Bouncing animal loading animation (needs illustration asset)
- Stars/clouds/rainbows in margins (purely decorative)
- Gradient background (adds complexity, dark warm is fine)
- Custom "All done for today" screen (v0.5 doesn't have time limits)

**Priya:** The "NEW" badge is great. When I add a video to a playlist, and my kid sees it's new — that's a tiny moment of delight for both of us. The loading shimmer matters too — right now when thumbnails are loading, what happens?

**Dev:** Right now? The card shows the title text below a blank space where the thumbnail will be. Coil loads it async. There's a flash of empty space, then the image appears. A shimmer would fix that.

**Maya:** OK so v0.5 kid UI improvements:
1. Warm dark palette (replace navy with earthy warm)
2. Green focus ring on cards
3. 12dp corner radius
4. "NEW" badge
5. Shimmer placeholder for thumbnails
6. Nunito Sans font
7. Minimal wordmark in top-left of HomeScreen (small, unobtrusive)

**Dev:** That's maybe 2-3 hours of work total. Very reasonable.

---

## Round 8: ConnectScreen Redesign

**Suki:** The ConnectScreen is the most important screen in the entire product. It's the parent's first impression. They just installed this APK they sideloaded, they're slightly nervous, and this screen either makes them feel "oh, this is nice" or "oh, this is janky."

**Current state:**
- Dark navy background
- QR code (white on black, 200dp)
- Coral monospace PIN (40sp, letter-spacing 8sp)
- "Charityware" text
- Unicode gear emoji (\\u2699) in bottom-right

**Proposed state:**
- **Light background** (#FAFAFA) — this is a parent screen, use the parent palette
- **ParentApproved wordmark** at the top (Nunito Sans Bold + green checkmark)
- **QR code** centered, larger (240dp), with a caption: "Scan with your phone"
- **PIN** below QR, in a green-bordered box: "Or enter this PIN: 847293"
- **Subtitle text**: "Open the camera app on your phone and point it at this code."
- **Version + TV ID** in small gray text at bottom (for debug/support)
- **Settings gear**: proper icon (from Material Icons), not a Unicode character

**Dev:** Switching ConnectScreen to light background means it'll flash white on a dark TV. But it's only shown during setup — the kid never sees it. And it needs to feel like the dashboard the parent is about to land on. So yes, light.

**Priya:** The "Scan with your phone" text is essential. I've used QR codes before but I still appreciate being told what to do. And "Or enter this PIN" for if scanning doesn't work — good fallback.

**Raj:** The ConnectScreen currently shows the full relay URL in the QR code, which includes the tv-secret. The visual design shouldn't make the QR code feel throwaway — parents should understand it's a one-time pairing step. Maybe a small lock icon near the QR? Not scary, just... present.

**Maya:** A small "one-time setup" label near the QR code. Not a lock icon — that's too security-forward. Just text.

**Suki:** I like that. The hierarchy becomes:
1. **ParentApproved** wordmark (top center)
2. **"One-time setup"** label (small, gray)
3. **QR code** (center, 240dp)
4. **"Scan with your phone's camera"** (body text)
5. **PIN box** ("Or enter: 847293")
6. **Version / TV ID** (bottom, tiny, gray)
7. **Settings gear** (bottom-right, Material icon)

---

## Round 9: Dashboard Alignment

**Suki:** The phone dashboard currently uses the dark navy theme with teal accents. It needs to switch to the parent palette: white background, green accents, Nunito Sans.

**Dev:** The dashboard is vanilla HTML/CSS/JS — no framework. The CSS changes are straightforward. I'll also need to update the relay copy (`relay/assets/style.css`) and the local copy (`tv-app/app/src/main/assets/style.css`).

**Maya:** Actually — do we still need the local copy? In v0.4, the dashboard is served from the relay edge.

**Dev:** We should keep the local copy for development and for the emergency local-access fallback (if relay is down, parent on same WiFi can hit `http://<tv-ip>:8080`). But the relay copy is the primary one. They should be identical — same CSS, same HTML, same JS.

**Suki:** Dashboard changes:
1. Background: #FAFAFA
2. Text: #1A1A1A
3. Accent/buttons: #22A559
4. Cards: white (#FFFFFF) with light border (#E5E7EB)
5. Font: Nunito Sans from Google Fonts
6. Header: "ParentApproved" wordmark (text + checkmark SVG)
7. Delete/destructive buttons: #DC2626 (red-600)
8. Stats numbers: #22A559 (green, not teal)
9. Offline banner: #FEF3C7 background (amber-50), #92400E text — warm warning, not alarming red

**Raj:** The auth PIN input on the dashboard — currently it's a teal-bordered text field. Make it green-bordered to match. And the "Authenticated" state should show the green checkmark. Visual consistency = trust.

**Priya:** One thing that always bugged me — when the dashboard shows "TV is offline," it currently shows a red banner. Red feels like something is broken. Can it be softer? Like "Your TV isn't connected right now. It'll reconnect when it's turned on."

**Maya:** Love it. Warm amber background, friendly language. The red-banner-on-white-page is for errors, not expected states. A TV being off is expected.

---

## Round 10: What Assets Do We Need?

**Maya:** Let me inventory what we actually need to produce for v0.5.

**Suki:** Here's the complete list:

### Font
- [ ] `NunitoSans-Variable.ttf` — bundle in TV app (`res/font/`)
- [ ] Google Fonts link in dashboard HTML and landing page

### Icon / Logo
- [ ] **Checkmark icon** — vector drawable (green circle + white rounded check)
  - Used as: Android TV banner, adaptive icon foreground, favicon, inline in wordmark
  - Format: XML vector drawable (TV), SVG (web), .ico (favicon)
- [ ] **Android TV banner** — 320x180dp, warm dark (#1C1917) background, centered checkmark icon, "ParentApproved" text below
- [ ] **Adaptive icon** — foreground: checkmark on transparent, background: #22A559 (green)
- [ ] **Favicon** — 32x32 checkmark, green on transparent

### No new illustration assets needed
- Skeleton shimmer: pure Compose (no image)
- "NEW" badge: pure Compose (yellow dot + text)
- Focus ring: pure Compose (border modifier)
- Settings gear: Material Icon (already in the dependency tree via material3)

**Dev:** So in terms of real files to produce:
1. One font file (download from Google Fonts)
2. One vector drawable (checkmark, ~10 lines of XML)
3. One banner drawable (update existing banner.xml)
4. Adaptive icon drawables (foreground + background)
5. One SVG for web (export from the same checkmark path)
6. One .ico file (convert from SVG)

That's it. No designer needed. No custom illustrations. No Lottie animations. Just a font, a checkmark, and color changes.

**Maya:** I'm comfortable with that scope. This is a branding alignment release, not a design overhaul.

---

## Round 11: What We're NOT Doing in v0.5

**Dev:** Let me be explicit about what's out of scope. This is important because the PRD described a very ambitious kid UI.

**NOT in v0.5:**
- Bouncing animal loading animation
- Stars, clouds, rainbows decorative elements
- Random card tilt
- Custom "Time's Up" screen (no time limits yet)
- Custom "No Network" illustration
- Gradient backgrounds
- "Sleeping character" illustrations
- Per-playlist emoji (unless already in the data model)
- Any video player chrome changes (ExoPlayer's default controller is fine)
- Landing page build (separate project, separate timeline)
- Push notification design
- Multi-profile UI
- Light mode toggle for the TV

**Suki:** Agreed. v0.5 is: **one font, one accent color, two palettes (warm dark + light), one icon, and visual consistency across all surfaces.** That's it. The decorative kid UI elements are v1.0 material, when we have a designer and real user feedback.

**Raj:** Nothing in v0.5 changes the security model. No new endpoints, no protocol changes, no auth flow changes. This is purely cosmetic. Which means: no new security tests needed, existing 250 tests should still pass.

---

## Round 12: Success Criteria

**Maya:** How do we know v0.5 is done?

1. **Scan test**: Parent scans QR code on TV. The ConnectScreen (light, green, wordmark) leads to a dashboard (light, green, wordmark) that looks like it belongs to the same product. No visual jarring.

2. **Squint test**: Hold up the TV screen and the phone screen side by side. Squint. The two screens should feel related — same green accent, same font shape, same energy — even though one is dark (kid) and one is light (parent).

3. **Name test**: The word "ParentApproved" appears on the ConnectScreen, the dashboard header, and the launcher. "KidsWatch" appears nowhere user-facing (only in package name and developer docs).

4. **Font test**: Every piece of text on every surface uses Nunito Sans. System fonts are gone.

5. **Color test**: The only accent color anywhere is forest green (#22A559). No coral. No teal. No navy. Warm dark (#1C1917) on kid screens, off-white (#FAFAFA) on parent screens.

6. **All 250 tests pass**: No regressions.

**Priya:** And for me: when I open the dashboard bookmark on my phone, it looks like something I'd trust. Clean, simple, not a developer tool. If my husband saw it, he wouldn't ask "what is this weird site?"

---

## Summary: v0.5 "The Polish" — Scope

| Change | Files touched | Effort |
|--------|---------------|--------|
| Rename to ParentApproved (user-facing) | strings.xml, HomeScreen, ConnectScreen, dashboard HTML | Small |
| Unified color system (warm dark + light) | Color.kt, Theme.kt, all screens, style.css (x2) | Medium |
| Nunito Sans font | res/font/, Theme.kt, style.css, dashboard HTML | Small |
| Checkmark icon + banner + adaptive icon | res/drawable/, res/mipmap-*/ | Small |
| ConnectScreen redesign (light, wordmark, better layout) | ConnectScreen.kt | Medium |
| Dashboard CSS overhaul (light theme, green accent) | style.css (x2), index.html (x2) | Medium |
| Kid UI polish (focus ring, 12dp radius, NEW badge, shimmer) | VideoCard.kt, HomeScreen.kt | Small |
| Settings gear → Material icon | ConnectScreen.kt | Tiny |
| Favicon + SVG for web | relay/assets/ | Tiny |

**Estimated total:** ~1 day of focused work.

**What it buys:** A product that looks like a product, not a prototype. Visual coherence across TV, phone, and (future) website. A brand identity (ParentApproved + green checkmark + Nunito Sans) that a parent can trust.

---

## Haiku

*Coral fades to green*
*one checkmark across all screens —*
*finally, a brand*
