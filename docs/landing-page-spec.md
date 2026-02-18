# ParentApproved.tv — Landing Page Spec (Beta)

**Domain:** parentapproved.tv
**Target:** Beta users — tech-capable parents (25-40) with Android TV, frustrated with YouTube Kids
**Goal:** APK downloads + GitHub stars + email signups for updates
**Tone:** Direct, empathetic, zero fluff. Parent-to-parent, not corporation-to-customer.

---

## SEO / Meta

**Title tag:** `ParentApproved.tv — Kid-Safe Video Player for Android TV | No Algorithm, No Ads`

**Meta description:** `Your kids watch ONLY the videos you pick. Works with YouTube™ playlists. No algorithm, no ads, no surprises. Free, open-source Android TV app. Manage from your phone.`

**Trademark notice (footer):** *ParentApproved.tv is not affiliated with YouTube or Google LLC. YouTube is a trademark of Google LLC.*

**OG image:** Screenshot of TV showing kid-friendly playlist grid + phone showing dashboard side by side

**Schema markup:** SoftwareApplication + FAQPage

---

## Page Structure

### 0. Sticky Top Bar (thin, dismissible)
```
🚧 Beta — Built by a parent, for parents. Help shape it → [Join Beta]
```
- `[Join Beta]` scrolls to download section
- Subtle, not aggressive

---

### 1. Hero Section

**Headline (H1):**
> You pick the videos.
> They watch only those.

**Subheadline:**
> A free, open-source Android TV app that plays videos from playlists you choose — nothing else. Works with YouTube™. No algorithm. No ads. No distractions.

**CTA buttons:**
- `[Download APK]` (primary, prominent) → scrolls to download section
- `[See How It Works]` (secondary, ghost) → scrolls to how-it-works

**Hero visual:**
- Split image: left = TV screen showing clean video grid (playlist rows, large thumbnails, no YouTube chrome), right = phone showing parent dashboard with playlist management
- Real screenshots, not mockups — authenticity matters for beta

---

### 2. The Problem (Empathy Section)

**Section headline:**
> In the age of infinite content, who curates your child's mind?

**Opening paragraph (large text, centered):**
> There are more videos uploaded every hour than your child could watch in a lifetime. Algorithms decide what they see next — optimized for watch time, not for your child's wellbeing. No algorithm knows your child. No company has your child's interests at heart the way you do. The only person qualified to curate what goes into your child's mind is you.

**Three problem cards (side by side on desktop, stacked on mobile):**

**Card 1: Infinite Content, Zero Curation**
- Icon: infinity symbol / flood
- "More video is uploaded every minute than anyone can review. Algorithms fill the gap — but they optimize for engagement, not education. Not safety. Not your values."

**Card 2: The Algorithm Problem**
- Icon: dice/shuffle
- "Video apps pick what plays next. You don't. 46% of parents report their kids saw inappropriate content — even with parental controls on."
- Source: Pew Research

**Card 3: The Ads & UI Problem**
- Icon: maze/rabbit hole
- "Search bars. Recommended videos. Autoplay. Ads for fast food. These interfaces are designed to keep watching — not to keep kids safe."

**Closing line (below cards):**
> Nobody else should decide what your child watches. Not an algorithm. Not a company. Not even us. You're the parent. You decide.

---

### 3. How It Works (3-Step Flow)

**Section headline:**
> Set up in 5 minutes. Manage from your phone.

**Step 1: Install on your TV**
- Visual: TV showing QR code / connect screen
- "Download the APK and install it on any Android TV — Mi Box, Chromecast, Shield, any Android TV box."

**Step 2: Connect your phone**
- Visual: Phone scanning QR code on TV
- "Scan the QR code on the TV. Enter the PIN. Your phone is now the remote control and the management dashboard."

**Step 3: Add playlists**
- Visual: Phone dashboard with "Add Playlist" field and a playlist appearing on the TV behind it
- "Paste any YouTube playlist URL. It appears on the TV instantly. Your kids see only what you've approved."

**Below the steps:**
> That's it. No account to create. No subscription. No cloud. Everything runs on your TV.

**Optional step (callout box, lighter styling):**
> **Want remote access?** In Settings, check "Enable Remote Access." Your TV connects to a relay so your dashboard works from anywhere — work, commute, grandma's house. The relay is free and run as charityware. This is fully optional — the app works completely locally without it.

---

### 4. What Your Kid Sees (TV Experience)

**Section headline:**
> Just videos. Nothing else.

**Full-width TV screenshot** showing the home screen with playlist rows.

**Feature callouts (overlaid or below):**
- ✓ Large thumbnails, easy to navigate with a remote
- ✓ D-pad friendly — up/down between playlists, left/right to scroll, OK to play
- ✓ No search bar. No recommendations. No comments. No ads.
- ✓ Video ends → next video in playlist plays automatically
- ✗ No way to browse YouTube. No way to search. No way to escape.

---

### 5. What You Control (Parent Dashboard)

**Section headline:**
> Your phone is the control panel.

**Dashboard screenshot** with annotated callouts:

| Feature | What it does |
|---------|-------------|
| Add/remove playlists | Paste a YouTube playlist URL. Remove with one tap. |
| Now Playing | See what's playing on the TV right now. |
| Play/Pause/Skip | Control playback from your phone — even from another room. |
| Watch history | See what your kids watched and for how long. |
| Works in any browser | No app to install. Safari, Chrome, whatever. |

**Note below:**
> The dashboard is a web page. In local mode, it's served directly from your TV over WiFi. With Remote Access enabled, it loads from the relay — same dashboard, works from anywhere. Either way: no account, no app to install.

---

### 6. Comparison Table

**Section headline:**
> How it compares

| | ParentApproved.tv | YouTube™ Kids | Kidoodle.TV | SmartTube |
|---|:---:|:---:|:---:|:---:|
| **Who picks content?** | You (playlists) | Algorithm | Company | Nobody (open browse) |
| **Ads?** | Never | Yes (unless Premium) | Yes (free tier) | No |
| **Video app UI?** | Clean, minimal | Full branded UI | Branded UI | Full branded UI |
| **Algorithm?** | None | Yes | Yes | Yes |
| **Android TV?** | ✓ | ✓ | ✓ | ✓ |
| **Manage from phone?** | ✓ Browser | ✓ App | ✗ | ✗ |
| **Open source?** | ✓ | ✗ | ✗ | ✓ |
| **Cost** | Free forever | Free w/ ads | $5/mo | Free |
| **Privacy** | Local-first (remote opt-in) | Google cloud | Cloud | Local |
| **Works offline?** | Yes (local mode) | No | No | Yes |

---

### 7. Differentiators (Why This Exists)

**Section headline:**
> Built different, on purpose.

**Four differentiator blocks:**

**🔒 Local-first by default. Remote only if you choose.**
> Out of the box, everything runs on your TV. Your phone connects over WiFi. No cloud. No account. No data leaves your home. Want to manage playlists from work or check what's playing while you're out? Enable Remote Access in Settings — it connects your TV to a lightweight relay so your dashboard works from anywhere. You choose. We don't decide for you.

**📖 Open source**
> Every line of code is on GitHub. Read it, audit it, fork it. We have nothing to hide because there's nothing to hide.
> `[View on GitHub →]`

**🚫 No business model**
> No subscription. No ads. No "premium tier." No data to sell. This is a parent side project, not a startup. If you find it useful, we suggest donating to [charity].

**🎯 Single-purpose**
> This app does one thing: play parent-approved videos on the TV. Currently works with YouTube™ playlists — support for other video sources is planned. It doesn't monitor screen time (yet). It doesn't filter the internet. It doesn't track your kid. It just plays what you picked.

---

### 8. Who It's For (and Not For)

**Section headline:**
> Is this for you?

**For you if:**
- ✓ You have an Android TV (Mi Box, Chromecast with Google TV, Shield, any Android TV box)
- ✓ You already know which YouTube playlists/channels your kids should watch
- ✓ You're comfortable installing an APK (we have a step-by-step guide)
- ✓ You want zero algorithm involvement in what your kids see

**Not for you if:**
- ✗ You want the app to pick "safe" content for you (we don't curate — you do)
- ✗ You need iOS / Apple TV / Roku / Fire TV support (Android TV only for now)
- ✗ You want a polished, Play Store app (this is beta, sideloaded)
- ✗ You're not comfortable with a 5-minute technical setup

---

### 9. Download & Setup (CTA Section)

**Section headline:**
> Ready to try it?

**Download card (prominent, centered):**
```
┌─────────────────────────────────────────────┐
│                                             │
│  ParentApproved.tv Beta                     │
│  v0.3 • Android TV • 12 MB                 │
│                                             │
│  [Download APK]  ←— big green button        │
│                                             │
│  Requires: Android TV (API 24+)             │
│  Works on: Mi Box, Chromecast w/ Google TV, │
│  NVIDIA Shield, generic Android TV boxes    │
│                                             │
│  [Step-by-step install guide →]             │
│                                             │
└─────────────────────────────────────────────┘
```

**Below the card:**
> **First time sideloading?** It takes 5 minutes. Our guide walks you through every step with screenshots.

**GitHub link:**
> Source code: `[github.com/user/kidswatch →]`

---

### 10. FAQ (AEO-Optimized)

**Section headline:**
> Questions

Implement as expandable accordion with FAQPage schema markup.

**Q: Is this really free?**
A: Yes. No catch. It's open-source software built by a parent. There's no company, no investors, no business model. If you find it useful, we suggest donating to [charity].

**Q: How is this different from YouTube Kids?**
A: YouTube Kids uses an algorithm to decide what your kid can watch. We don't. You paste YouTube playlist URLs, and your kid sees ONLY those videos. No search bar, no recommendations, no algorithm.

**Q: Is sideloading safe?**
A: Sideloading means installing an app outside the Play Store. It's a standard Android feature. Our app is open-source — you can read every line of code on GitHub. We don't collect data, don't require permissions beyond network and storage, and don't phone home.

**Q: Do I need a YouTube account or API key?**
A: No. The app uses NewPipeExtractor (the same technology behind the NewPipe app) to play videos. No YouTube account, no Google sign-in, no API key.

**Q: Does it work without internet?**
A: The TV needs internet to stream YouTube videos. But all your playlists and settings are stored locally on the TV — they survive reboots and don't depend on any cloud service.

**Q: Can my kid escape the app?**
A: The app has no search, no settings accessible to kids, and no way to browse YouTube. The Home button on the remote will exit to the Android TV launcher — that's an OS limitation. For dedicated kid devices, we recommend setting ParentApproved.tv as the default launcher (guide coming soon).

**Q: What Android TV devices does it work on?**
A: Any device running Android TV with API 24+ (Android 7.0+). Tested on Xiaomi Mi Box 4 (Android 9) and Android TV emulator (API 34). Works on Chromecast with Google TV, NVIDIA Shield, and generic Android TV boxes.

**Q: Can I manage it remotely (not on the same WiFi)?**
A: Yes — it's opt-in. Go to Settings on the TV and check "Enable Remote Access." Your TV connects to a lightweight relay, and your dashboard works from anywhere. The relay is free charityware. If you prefer fully local, just leave it off — the app works entirely over your home WiFi with zero cloud dependency.

**Q: What is the relay? Is my data going through someone else's server?**
A: The relay is a tiny bridge that forwards your dashboard requests to your TV. It doesn't store playlists, videos, PINs, or watch history — it's a transparent pipe. Your TV is still the backend. All data stays in your TV's local database. The relay is open-source too — you can audit or self-host it.

**Q: What happens if YouTube changes something and videos stop playing?**
A: NewPipeExtractor occasionally needs updates when YouTube changes its internals. When this happens, we'll release an updated APK. Join the mailing list or watch the GitHub repo to get notified.

**Q: I found a bug / have a feature request.**
A: Open an issue on GitHub. We're actively developing and your feedback directly shapes what gets built next.

---

### 11. Footer

**Left:** ParentApproved.tv — Open source, local-first, parent-controlled video player for kids.

**Center links:**
- GitHub
- Install Guide
- Privacy (one line: "We collect nothing. All data stays on your TV.")
- Contact

**Right:**
- Email signup: "Get notified of updates (we email rarely)"
- Simple email field + submit. No name, no tracking.

**Trademark disclaimer:**
> *ParentApproved.tv is not affiliated with YouTube or Google LLC. YouTube is a trademark of Google LLC.*

**Bottom line:**
> Built by a parent who codes, for parents who care. Not a company. Not a startup. Just a better way to let kids watch videos.

---

## Design Notes

### Visual Style
- **Clean, confident, not playful.** This is a parent-facing page, not a kid-facing app. The TV screenshots show the playful kid UI — the page itself should feel trustworthy and competent.
- **Color palette:** White background, dark text (#1a1a1a), accent green for CTAs (#22c55e), subtle warm grays. Not rainbow. Not corporate blue.
- **Typography:** Inter or similar clean sans-serif. Large, readable. 18px body minimum.
- **Screenshots:** Real, not mockups. Show the actual app on a TV and actual dashboard on a phone. Authenticity > polish for beta.

### Technical Implementation
- **Static HTML/CSS/JS.** No React, no Next.js, no build step. This is a landing page.
- **Host on Cloudflare Pages** (free, fast, parentapproved.tv pointed via Cloudflare DNS).
- **< 100KB total page weight.** No hero videos, no heavy animations.
- **Mobile-first.** Parents will find this on their phone. Every section must work on a 375px screen.
- **Core Web Vitals:** LCP < 1s, no layout shift, instant interactivity.
- **No cookies, no analytics initially.** Add privacy-respecting analytics (Plausible or Fathom) later if needed.

### Above-the-fold Priority
On mobile, the first screen shows:
1. Headline ("You pick the videos. They watch only those.")
2. Subheadline (one sentence)
3. Hero image (TV + phone)
4. CTA button ("Download APK")

Nothing else. No navigation bar eating space. No cookie banner.

---

## Content Checklist

- [ ] Hero screenshot: TV home screen with playlists
- [ ] Hero screenshot: Phone dashboard
- [ ] Combined hero visual (TV + phone side by side)
- [ ] 3-step setup visuals (QR code, phone scanning, playlist adding)
- [ ] Full TV home screen screenshot (for "What Your Kid Sees")
- [ ] Annotated dashboard screenshot (for "What You Control")
- [ ] APK file hosted (GitHub releases or direct download)
- [ ] Install guide page (separate page, linked from landing page)
- [ ] GitHub repo link
- [ ] Email signup integration (Buttondown, Listmonk, or similar privacy-respecting service)
- [ ] FAQPage schema JSON-LD
- [ ] SoftwareApplication schema JSON-LD
- [ ] OG image (1200x630)
- [ ] Favicon

---

## SEO Pages (Phase 2 — after launch)

These are separate blog/content pages to drive organic traffic:

1. **"The Ultimate Guide to Safe YouTube for Kids on Android TV (2026)"**
   - Target: "safe youtube for kids android tv"
   - Comprehensive guide, positions ParentApproved.tv as the answer

2. **"ParentApproved.tv vs YouTube Kids: Which Is Safer?"**
   - Target: "youtube kids alternative", "better than youtube kids"
   - Side-by-side comparison with evidence

3. **"How to Sideload Apps on Android TV (Step-by-Step)"**
   - Target: "sideload apps android tv"
   - Doubles as the install guide, captures search traffic

4. **"Why YouTube Kids' Algorithm Is Still Dangerous"**
   - Target: "youtube kids algorithm problems"
   - Problem-awareness content, links to ParentApproved.tv as solution

5. **"10 Educational YouTube Playlists for Kids"**
   - Target: "best educational youtube playlists for kids"
   - Shareable, useful, naturally links to "use these with ParentApproved.tv"

---

## Key Metrics (Beta)

| Metric | Target (3 months) |
|--------|-------------------|
| APK downloads | 500 |
| GitHub stars | 100 |
| Email signups | 200 |
| Install guide completion | 60% of downloads |
| Referral source | Track via UTM (Reddit, HN, XDA, organic) |

---

## Distribution Plan (Beta Launch)

| Channel | Action | Timing |
|---------|--------|--------|
| Reddit r/AndroidTV | "I built a kid-safe YouTube app for Android TV" post | Week 1 |
| Reddit r/Parenting | "Frustrated with YouTube Kids — so I built an alternative" | Week 1 |
| Reddit r/selfhosted | "Local-first YouTube player for kids (no cloud)" | Week 2 |
| XDA Forums | App thread with sideload instructions | Week 1 |
| Hacker News | "Show HN: ParentApproved.tv — playlist-only YouTube for Android TV" | Week 2 |
| Product Hunt | Launch with screenshots + demo video | Week 3 |
| GitHub | Well-written README with screenshots, badges | Day 1 |
| r/degoogle | "YouTube for kids without a Google account" | Week 2 |
| Parent Facebook groups | Organic sharing (ask a friend to post) | Week 3 |
