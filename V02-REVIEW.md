# V0.2 Spec Review — PM & Dev Manager Discussion

---

## Round 1: Independent Reviews

### Product Manager (Sarah) — First Read

Reading through the spec...

**What I like:**
- The "dumb backend" principle is great. Fewer moving parts = fewer things to break = faster to ship. I've seen too many V1s die because the backend was overengineered.
- Playlist-as-curation is the killer insight. Parents don't want to learn a new tool. They already make YouTube playlists for their kids. We're just putting a lock on the TV.
- Play event recording now, analytics later — smart. Collect the data while we can, build the dashboard when we know what parents actually want to see.

**Concerns:**

1. **Onboarding is too many steps.** Sign up on website → add playlists → get pairing code → go to TV → enter code. That's 5 steps before a kid watches anything. For V0.2 I'd accept this but we need to think about whether we can collapse some of these. Could we skip the website entirely for V0.2 and let parents enter playlist URLs directly on the TV? No — that's terrible UX on a remote. OK, the website is necessary.

2. **The spec doesn't define the first-run experience on the TV.** What does the kid see before pairing? A blank screen? An error? We need a pairing screen that's clear: "Ask your parent to visit kidswatch.app and get your TV code." Show the code prominently.

3. **What happens when a playlist is empty or deleted on YouTube?** The spec doesn't cover error states. If a parent adds a playlist and later deletes it on YouTube, the TV will try to resolve it and fail. We need a graceful "this playlist is no longer available" state, not a crash.

4. **No mention of thumbnail loading strategy.** On a TV with a grid of thumbnails, we're potentially loading 50-100 images. Do we lazy-load? Use YouTube's thumbnail CDN directly (yt.img.com)? Cache thumbnails on disk? This is a real UX concern — slow thumbnail loading makes the app feel broken.

5. **The "parent PIN" for settings is mentioned but not specced.** Is it a 4-digit PIN? Where is it stored? Can it be reset? This is a V0.2 must-have if we claim the kid can't access settings.

6. **Auto-advance is listed as an open question but it shouldn't be.** For a kids app, auto-advance within the playlist is the obvious answer. Kids don't want to navigate back to the grid after every 3-minute video. Auto-advance, and return to the grid when the playlist ends.

7. **No mention of what "grouped by playlist" looks like.** Is it Netflix-style horizontal rows? A simple vertical list? A Leanback-style grid? The TV UI needs to be dead simple — a kid (age 3-8?) needs to navigate it with a D-pad. I'd say: one row per playlist, horizontal scroll within the row, vertical scroll between rows. Exactly like the Android TV Leanback pattern.

---

### Dev Manager (Marcus) — First Read

Reading through the spec...

**What I like:**
- The architecture is extremely clean. Backend is stateless (aside from the data store), no cron, no queues. This could genuinely be a Supabase project with Row Level Security and zero custom backend code.
- Two-phase NewPipe (browse-time vs play-time) is the right decomposition. Playlist resolution and stream extraction are fundamentally different operations with different caching needs.
- Fire-and-forget play events with local queueing is the right pattern. No risk of data loss blocking playback.

**Concerns:**

1. **NewPipe playlist resolution is not in the feasibility test.** We tested single-video extraction in Test 4. But resolving a full playlist (50-200 videos with metadata) is a different operation — `PlaylistExtractor` vs `StreamExtractor`. We need to validate: Does it work? How long does it take for a 50-video playlist? Does it return thumbnails? This should be a Test 5 before we commit to the architecture.

2. **Client-side playlist resolution in the browser is a real problem.** The spec waves at "oEmbed or lightweight extraction" but this is unsolved. YouTube oEmbed doesn't return video lists — it returns a single embed for the playlist. YouTube Data API v3 requires an API key and has quota limits (10,000 units/day free). There's no NewPipe equivalent in JavaScript. Options:
   - **(a)** Just store the playlist ID on the website, show the YouTube playlist title only (from oEmbed), and let the TV be the only place that resolves the full video list. Simplest.
   - **(b)** Use YouTube Data API with an API key for the website's preview. Adds a dependency but the quota is generous for V0.2 scale.
   - **(c)** Use an iframe embed of the playlist on the website for preview (no API needed). Janky but works.
   - I'd recommend **(a)** for V0.2. The website just needs to confirm "yes, this is a valid playlist" and show its title. The TV does the real work.

3. **The 1-hour cache TTL feels arbitrary.** What are we optimizing for? If a parent adds a video to a playlist, when do they expect it to show up on the TV? If the answer is "soon," 1 hour might be too long. If the answer is "whenever," it's fine. I'd suggest: resolve on app launch (always), and keep the cache for within-session use. Don't bother with hourly refresh if the app isn't in the foreground.

4. **Pairing code collision.** `BEAR-1234` is roughly `~26^4 * 10^4 = ~4.5 billion` combinations if it's WORD-NNNN. But if we use a smaller word list (say 100 common words), it's `100 * 10000 = 1M`. At V0.2 scale (tens of families) this doesn't matter. But the spec should define the format: how many characters, what alphabet, expiry?

5. **The spec doesn't mention app update strategy.** NewPipe will break when YouTube changes. How do we push an update to the TV app? Google Play Store for Android TV? Sideload? This is critical — if the extraction breaks, the whole app is dead until the user updates.

6. **ExoPlayer stream selection needs a strategy.** The feasibility test always picked "best progressive 360p." For a TV app on a big screen, 360p looks terrible. We should prefer the highest quality progressive stream, or use adaptive (DASH) with MergingMediaSource for video-only + audio. The spec should state: prefer highest available quality up to 1080p, fall back to progressive if adaptive fails.

7. **Supabase/Firebase choice has implications.** If we go Supabase: Postgres + Row Level Security + built-in auth + realtime subscriptions (free for TV polling). If Firebase: Firestore + Firebase Auth + Cloud Functions (if we ever need compute). I'd lean Supabase — the Postgres model is cleaner for the data we're storing, and RLS means truly zero backend code. But we should decide now because it affects the parent website's auth integration.

---

## Round 2: Reading Each Other's Comments

### Sarah (PM) reads Marcus's comments

Marcus raises a critical point about **Test 5 for playlist resolution** — I agree. We can't ship this architecture without validating that NewPipe can resolve playlists efficiently. If it takes 30 seconds to resolve a 100-video playlist, we have a loading UX problem.

On **browser-side resolution** — I agree with option (a). For V0.2, the website just needs to confirm the playlist exists and show a title. Parents will go to the TV to see the actual videos. We're overthinking the website preview.

On **cache TTL** — Marcus is right that "resolve on launch, cache within session" is simpler and more intuitive than a timer. Parents will tell their kid "I added new videos, restart the app" and it'll just work. No explaining cache timers.

On **stream quality** — Good catch. 360p on a 55" TV is unwatchable. We need 720p minimum, 1080p preferred. This should be a stated requirement, not an open question.

On **app updates** — This is a real risk I missed. If NewPipe breaks and the app is on the Play Store, we're looking at 1-3 days for review. That's 1-3 days of a dead app. We should think about a remote config for the NewPipe version or extraction logic, but that's overengineering for V0.2. For now: Play Store distribution, fast response to NewPipe breakage, and maybe a user-visible "extraction failed, try again later" message.

I'll add **Test 5** to the plan before any V0.2 development starts.

### Marcus (Dev) reads Sarah's comments

Sarah's point about **onboarding steps** is valid but I think it's acceptable for V0.2. The pairing flow is standard (Roku, Disney+, Netflix all do this). We just need to make it clear.

On the **first-run TV experience** — absolutely right, this needs to be specced. I'd add: show a large pairing code on the TV screen with the URL `kidswatch.app`. The code should be auto-generated on first launch and registered with the backend immediately (TV calls `POST /pair/register` with its device ID to claim a code). Parent then "activates" the code on the website by adding it to their family. This is slightly different from the current spec where the TV sends a code — it's actually cleaner if the TV generates and displays a code, and the parent enters it on the website.

Wait — actually the current spec says "TV sends pairing code." That means the parent sees the code on the website and types it into the TV. That's the wrong direction for a TV app. Typing on a TV remote is painful. It should be:
- **TV displays a code** (auto-generated, registered with backend)
- **Parent enters the code on the website** (they have a real keyboard)

This is how every streaming service does it. The spec has the flow backwards.

On **empty/deleted playlists** — good catch. We need three states per playlist row on TV: loading, loaded, and error. Error = "Playlist unavailable" with a subtle message, doesn't crash the app.

On **thumbnail loading** — YouTube's `i.ytimg.com` CDN is fast and doesn't require auth. We'll use Coil (Compose image loader) with disk caching. Thumbnails are small (~15KB each at mqdefault quality). For 100 videos that's 1.5MB — trivial. Load them lazily as rows scroll into view.

On **auto-advance** — agreed, this shouldn't be an open question. Auto-advance within playlist, return to grid at end.

On **parent PIN** — for V0.2, a simple 4-digit PIN stored locally on the device (SharedPreferences, encrypted). No recovery flow — if forgotten, factory reset the app. Keep it simple.

---

## Agreements & Resolved Questions

After both rounds, we agree on:

1. **Test 5 (playlist resolution) must happen before V0.2 dev starts** — validate NewPipe `PlaylistExtractor` for speed, metadata quality, and large playlists
2. **Pairing flow is reversed** — TV displays code, parent enters it on website (not the other way around)
3. **Website doesn't resolve video lists** — just validates playlist URL (oEmbed for title), stores playlist ID. TV does all resolution.
4. **Cache strategy: resolve on launch, cache within session** — no hourly timer. Parent says "restart the app" to pick up new videos.
5. **Auto-advance within playlist**, return to grid at playlist end
6. **Stream quality: prefer highest progressive up to 1080p**, fall back to adaptive merge if needed
7. **Parent PIN: 4-digit, local storage, no recovery** (V0.2)
8. **TV UI: Leanback-style rows** — one row per playlist, horizontal scroll within, vertical between
9. **Thumbnail loading: Coil + YouTube CDN** (`i.ytimg.com/vi/{id}/mqdefault.jpg`), lazy load, disk cache
10. **Backend: Supabase** — Postgres + RLS + built-in auth. Zero custom backend code.
11. **Error states must be handled:** playlist unavailable, extraction failed, network down — all get user-visible messages, never crashes
12. **Distribution: Google Play Store for Android TV** — accept the update latency risk for V0.2
