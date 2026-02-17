# KidsWatch — What It Is and How It Works

*For parent testers. V0.2 is an early test build, not a polished product.*

---

## The Problem

You want your kid to watch YouTube on the TV, but YouTube is a minefield. They start on Peppa Pig and end up watching unboxing videos, then reaction videos, then who-knows-what. You can't sit next to them every minute.

YouTube Kids exists but the recommendations are still weird, the content filtering is hit-or-miss, and your kid keeps navigating to things you didn't choose.

## What KidsWatch Does

You pick YouTube playlists. Your kid sees only those playlists on the TV. Nothing else.

- **No search bar.** There is no way for your kid to search for anything.
- **No recommendations.** No "Up Next," no sidebar, no algorithm.
- **No YouTube UI at all.** The app plays videos directly — it doesn't show YouTube's interface.
- **You control the content** by managing normal YouTube playlists you already know how to make.

## How It Works

### One-Time Setup (~5 minutes)

1. **Install the app on your Android TV.** For now, we'll send you an APK file. You install it via USB or a file manager. (This is a test build — it'll be on the Play Store later.)

2. **The TV shows a code.** Something like `MXKP-7291`. Leave this screen up.

3. **On your phone or laptop, go to kidswatch.app.** Sign in with your Google account (the same one you use for YouTube).

4. **Enter the code from the TV.** The TV will instantly say "Paired!" and you're connected.

5. **Add a playlist.** Go to YouTube, find a playlist you like (or make one), copy the URL, paste it into kidswatch.app. The playlist shows up on the TV within seconds.

That's it. Add as many playlists as you want. The TV shows them all.

### Ongoing Use

- **Adding videos:** Add videos to your YouTube playlists like you normally would. Next time the TV app refreshes (on launch, or tap the refresh button), the new videos appear.
- **Adding playlists:** Paste a new playlist URL on the website. It appears on the TV instantly.
- **Removing playlists:** Remove it on the website. It disappears from the TV instantly.
- **Your kid just uses the remote.** Arrow keys to browse, enter to play. That's it.

### What Your Kid Sees

A simple screen with rows of video thumbnails — one row per playlist. They pick a video, it plays full-screen. When it ends, the next video in the playlist plays automatically. They can press Back to go back to the video list.

There are no buttons to go to YouTube, no search, no comments, no ads (we're not using YouTube's player), no related videos.

---

## What Works in This Test Build

- Sign in with Google on the website
- Add/remove public YouTube playlists
- Pair the TV with a code
- Playlists sync to the TV in real time
- Videos play at HD quality (720p-1080p)
- Auto-advance to next video in playlist
- Basic remote control: play/pause, seek, back

## What Doesn't Work Yet

- **No time limits.** Your kid can watch as long as they want. (Coming in next version.)
- **No PIN lock.** There's a Settings menu on the TV that your kid could accidentally access. It won't do anything harmful — worst case they restart the pairing process. (PIN coming in next version.)
- **No watch history for parents.** We're recording what your kid watches, but there's no dashboard to see it yet. (Coming in next version.)
- **Public playlists only.** If your playlist is set to Private or Unlisted on YouTube, it won't work. Make sure your playlists are Public.
- **No Play Store yet.** You have to install the APK manually. We'll walk you through it.

## What We Want Your Feedback On

1. **Is the setup flow clear?** Did you get stuck anywhere during pairing or adding playlists?
2. **Does your kid understand the TV interface?** Can they browse and pick videos without help?
3. **Is the video quality OK?** Any buffering, glitches, or unwatchable quality?
4. **Do you actually use YouTube playlists?** If not, what would be easier — picking individual videos? Pasting video URLs one by one?
5. **What's missing?** What's the first thing you wish it had?
6. **Would you trust this enough to leave your kid alone with it?** If not, what would make you trust it?

---

## Technical Notes (For the Curious)

- The app doesn't use YouTube's website or embed player. It extracts the video stream URL and plays it with a native Android video player. This is how we eliminate all YouTube UI.
- The extraction uses an open-source library called NewPipeExtractor (same tech behind the NewPipe app).
- YouTube occasionally changes things that break the extraction. When that happens, we'll need to push an update. During testing, this might mean sending you a new APK.
- The website is a simple single-page app. Your Google account is used only for authentication — we don't access your YouTube account, your watch history, or anything else.
- All data is stored in Firebase (Google's cloud). Your playlist URLs, TV pairing info, and your kid's watch events. Nothing else.

---

*Questions? Text/message the person who sent you this. We're actively developing and your feedback directly shapes what we build next.*
