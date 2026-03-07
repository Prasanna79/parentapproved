# ParentApproved.tv — Launch Posts (Final)

---

## Twitter/X Thread

**Tweet 1 (Hook):**
I built a kid-safe video player for Android TV because I was tired of my son starting on dinosaur videos and ending up on... I don't even know what.

It's called ParentApproved.tv. It's free, open source, and in beta. Here's the story. [thread]

**Tweet 2:**
The problem with YouTube isn't that it exists. It's that you hand your kid the remote and an algorithm decides what plays next.

YouTube Kids is better, but it still recommends stuff I wouldn't pick. I didn't want "better algorithm." I wanted no algorithm.

**Tweet 3:**
So I built an Android TV app where:

- You paste YouTube playlist URLs from your phone
- Kids see ONLY those videos on the TV
- No search bar, no recommendations, no ads
- No account needed, no data leaves your TV

You curate. It plays.

**Tweet 4:**
Tech details for the curious:

- Kotlin + Jetpack Compose
- ExoPlayer for playback
- Embedded web server — manage from any phone on your WiFi
- Cloudflare Workers relay for remote access
- Open source (GitHub link in bio)

No API key. No Google sign-in. Local-first.

**Tweet 5:**
This is a beta. Some edges are rough. I'm a parent who codes, not a funded startup.

If you have an Android TV box and kids who watch videos, I'd genuinely love your feedback.

parentapproved.tv

#AndroidTV #OpenSource #KidSafe

---

## LinkedIn

I've been working on a side project that came from a pretty mundane parenting moment: my 4-year-old watching a dinosaur video that auto-played into something I had to grab the remote for.

YouTube isn't going anywhere in our house. I'm not anti-screen. But I wanted to pick exactly which videos my kid watches — and not rely on an algorithm to figure that out for me.

So I built ParentApproved.tv.

It's an Android TV app. You paste YouTube playlist URLs from a dashboard on your phone. The TV plays only those videos. No search bar, no recommendations, no ads, no account needed. Everything runs locally on the TV.

It's open source, free, and currently in beta.

I'm not pretending it's finished — there are rough edges. But it works, and it's been running on our TV for months. My kid watches the playlists I picked, and that's it.

If you're a parent with an Android TV device, or you just like poking at open source projects, I'd love to hear what you think.

parentapproved.tv

#AndroidTV #OpenSource #ParentalControls

---

## Reddit: r/AndroidTV

**Title:** I built a free, open-source video player for Android TV — lets parents pick exactly which playlists kids can watch

**Body:**

Hey everyone. I built an Android TV app called ParentApproved.tv and wanted to share it here.

**What it does:** You paste YouTube playlist URLs into a dashboard on your phone. The TV plays only those videos. No YouTube UI, no search, no recommendations, no ads. Kids just see the playlists and press play.

**How it works:**
- Sideload the APK (no Play Store, it's open source)
- The app runs a small web server on the TV
- Scan a QR code from your phone to open the dashboard
- Add playlists, control playback, see what's playing — all from your phone
- Optional Cloudflare relay for managing your TV when you're not home

**Tech stack:** Kotlin, Jetpack Compose, ExoPlayer, Ktor embedded server, Room DB. Video extraction via NewPipeExtractor — no API key, no sign-in.

It's in beta. Some things are rough. But it's been running on my Mi Box for a while now and it's been solid.

APK + source: parentapproved.tv (links to GitHub)

Happy to answer any technical questions.

---

## Reddit: r/Parenting

**Title:** I got tired of my kid's YouTube rabbit holes, so I built an app where I pick every video

**Body:**

My son is 4. He loves watching videos on our TV. I'm fine with that — screen time is part of life, and I'm not going to pretend otherwise.

What I'm not fine with is the algorithm. He'd start on a dinosaur playlist and ten minutes later he's watching some bizarre toy unboxing thing with millions of views that I'd never choose for him.

YouTube Kids was better but still not what I wanted. I didn't want a "safer algorithm." I wanted no algorithm at all.

So I built something. It's an Android TV app called ParentApproved.tv. I paste YouTube playlist URLs from my phone, and the TV plays only those videos. No search bar, no recommendations, no ads. He sees dinosaurs, science experiments, and Sesame Street — because those are the playlists I added.

It's free, open source, and there's no account to create. Everything stays on the TV.

It's also a beta — I'm still working on it. I've been using it at home for months and it's been great for us, but I know it's not for every family or every setup.

I'm curious if other parents would find this useful, and I'd love feedback if you try it.

parentapproved.tv

---

## Reddit: r/opensource

**Title:** ParentApproved.tv — open-source Android TV app for parent-curated kid video playback

**Body:**

I've been building an open-source Android TV app that solves a specific problem: letting parents control exactly which videos their kids can watch, with zero algorithmic recommendations.

**Architecture:**
- Kotlin + Jetpack Compose TV app
- ExoPlayer for playback, NewPipeExtractor for video extraction (no API key)
- Embedded Ktor server on the TV serves a parent dashboard (HTML/JS)
- Room DB for local storage — no cloud accounts, no data leaves the device
- Optional Cloudflare Workers relay (also open source) for remote access via WebSocket

**What makes it different from parental control apps:** It's not a filter or a blocker. Parents paste playlist URLs. Kids see only those playlists. That's it.

License is open source, charityware (mettavipassana.org). Currently in beta — v0.9.

Feedback, issues, and PRs welcome.

GitHub: [link from parentapproved.tv]

---

## Notes on Humanization (Passes Applied)

### Pass 1: Remove AI tells
- Removed "I'm thrilled/excited to share"
- Removed "In today's digital age"
- Removed "Here's the thing"
- Removed em-dash overuse (kept 1-2 max per post)
- Removed "Let me tell you about"
- Removed "game-changer" / "journey"

### Pass 2: Make it sound like a person typing
- Added contractions everywhere (it's, I'm, I'd, don't, wasn't)
- Made sentences uneven length (short after long, fragments okay)
- Used "I" not "we" — it's one person
- Kept some rough phrasing on purpose (real people don't polish every line)
- Used specifics over generics ("my 4-year-old" not "children", "dinosaur videos" not "content")
- Removed bullet parallelism in places (not every item needs the same structure)

### Pass 3: SEO check
- "kid-safe video player" appears in Twitter hook
- "Android TV" in all posts naturally
- "YouTube alternative" implied but not forced (would sound like ad copy)
- "open source" in all posts
- "parental controls" in LinkedIn hashtag and r/AndroidTV context
- "no algorithm" repeated across posts (primary differentiator, also a search phrase)
- Long-tail: "parent-approved videos" in r/opensource title
- All posts link to parentapproved.tv (domain itself contains keywords)
- Reddit titles are searchable and contain key phrases
