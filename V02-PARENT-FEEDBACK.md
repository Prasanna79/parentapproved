# Parent Tester Feedback — Ravi (Techie Dad, 2 kids ages 4 and 7)

*Ravi is a software engineer. He has an Android TV (Chromecast with Google TV). His kids watch YouTube daily. He has a few YouTube playlists already — mostly nursery rhymes for the younger one and science experiment videos for the older one. He understands this is V0.2.*

---

## First Impression

> OK I like the pitch. "You pick playlists, kid sees only those" — that's exactly what I want. I already have playlists. I've been looking for something like this. YouTube Kids is garbage — the recommendations are insane and my 4-year-old somehow ends up on toy unboxing compilations every time.

## Reading Through the Setup

> Sideloading an APK — fine, I can handle that. But my wife can't. For the test that's OK. Play Store later is a must.

> Sign in with Google — good. One less password.

> Pairing with a code — makes sense, this is how Disney+ works. My only question: **do I need to be near the TV to read the code?** Can I do this while the TV is in the other room? I guess not, since the code is on the screen. That's fine, one-time thing.

> Adding a playlist by URL — this is where I pause. **How do I get a playlist URL on my phone?** If I'm in the YouTube app on my phone, I can share a playlist but the share URL format might be different from the browser URL. Does the website handle `youtube.com/playlist?list=PLxxx` AND the share URL format from the YouTube app? That matters because most parents will be on their phone, not a laptop.

## The TV Experience

> Rows of thumbnails, one per playlist — that makes sense. My 7-year-old can handle that. My 4-year-old... maybe. She'll need me to show her once. Arrow keys and enter — she already uses the remote for Netflix, so probably fine.

> Auto-advance is great. Nursery rhyme playlists have 30+ two-minute videos. Without auto-advance, my kid would be pressing buttons every 2 minutes.

> **720p-1080p quality — is that guaranteed?** Some kids' videos on YouTube are only in 480p (older nursery rhyme uploads). What happens then? I assume it falls back to whatever is available? That's fine, just want to know.

## Concerns

> **No time limits is my biggest concern.** I get that it's V0.3, but honestly, my kid will sit there for 3 hours if I let her. The first thing I'd want after the basic flow works is "stop after X minutes." Even a dumb timer would be better than nothing. But OK, V0.3, I'll wait.

> **No PIN — I'm actually fine with this for testing.** My kids don't go into settings menus. They don't even know what a gear icon means. If they accidentally hit it, worst case is what? They see some debug stuff? Re-pair? I'll just re-pair, no big deal.

> **"No ads" because you're not using YouTube's player — interesting.** So even without Premium, there are no ads? That's a huge selling point. But what about sponsored content embedded in the video itself? I guess that's on me to curate. Fair enough.

> **NewPipe extraction can break.** This is my main technical worry. How often does this actually break in practice? Once a month? Once a year? And when it breaks, how long until a fix? If my kid sits down to watch and it doesn't work, I have a meltdown on my hands. Can you show a friendlier error? Not "Can't play this video" but maybe "Taking a break! Try again later" with a cute illustration? Small thing but it matters for the kid-facing UX.

## Feedback on the Questions Asked

**1. Is the setup flow clear?**
> Yes, straightforward. My only concern is getting the playlist URL from the YouTube app on my phone (share sheet vs browser URL).

**2. Does your kid understand the TV interface?**
> Haven't tested yet, but rows of thumbnails + remote = my 7-year-old will figure it out instantly. 4-year-old needs one demo.

**3. Video quality?**
> Need to test. Fine with 720p.

**4. Do you actually use YouTube playlists?**
> YES. I have 4 playlists right now. This is the #1 reason I'm interested. But — **I also sometimes just want to add a single video that's not in a playlist.** Like my kid's favorite counting song. Do I have to create a whole playlist for one video? Could there be a "Favorites" or "Single Videos" playlist that I just add individual video URLs to? You manage the playlist, I just paste videos into it?

**5. What's missing?**
> In order of priority:
> 1. Time limits (you said V0.3, fine)
> 2. A way to add single videos, not just playlists
> 3. Watch history for parents ("what did she watch today?")
> 4. Multiple profiles (separate playlists for each kid)
> 5. The ability to reorder which playlists appear first on the TV

**6. Trust enough to leave kid alone?**
> For the basic flow — yes, actually. If the content is only from my playlists and there's truly no way to navigate out, I'd trust it. The main risk isn't content, it's time. Fix that and I'm solid.

## Summary

> This solves a real problem I have right now. The playlist-based approach is smart because I already have the playlists. The main thing I want that's missing is time limits, and a way to add individual videos without creating a YouTube playlist for each one. I'd use this today, even in test form, if you can walk me through the sideload.

> **One more thing:** can you make the pairing screen show a QR code alongside the text code? I know you said no camera on TV, but I mean for the other direction — I scan the QR on my phone and it takes me to kidswatch.app with the code pre-filled. Saves me typing the code. Most streaming services do this now.

---

## Action Items from Parent Feedback

1. **YouTube app share URL format** — verify the website handles `youtube.com/playlist?list=PLxxx`, `youtu.be` share links, and the share-sheet format from the YouTube mobile app
2. **Single video addition** — not in V0.2, but add to V0.3 consideration. Could be a "Favorites" playlist managed by KidsWatch, where parent pastes individual video URLs
3. **QR code on pairing screen** — low effort, high UX improvement. QR encodes `https://kidswatch.app/pair?code=ABCD-1234`. Phone scans → opens website → code is pre-filled. Add to V0.2 if easy.
4. **Friendlier error messages for kids** — "Taking a break! Try again later" instead of technical errors. Low effort, do in V0.2 polish.
5. **Quality fallback clarity** — document that stream quality falls back gracefully. 480p plays fine if that's all that's available.
6. **Time limits** — confirmed #1 priority for V0.3
