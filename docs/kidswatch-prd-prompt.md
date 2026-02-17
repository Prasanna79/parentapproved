# KidsWatch — PRD Discussion Prompt

Use this prompt with Claude (or any LLM) to generate a detailed, implementation-ready PRD.

---

## PROMPT

I want to build an open-source, free (donation-based) app called **KidsWatch** that gives kids a safe, locked-down YouTube viewing experience on Android TV. Parents control everything from their phone/laptop — the TV app is kid-mode only with no settings, no escape, no YouTube UI.

Here's the full context. Please produce a comprehensive PRD that a developer (or Claude Code) can use to build this.

### The Core Concept

**Three components:**

1. **Parent Web Dashboard** (Next.js, hosted on Vercel) — where parents sign in with Google, select YouTube playlists, configure time limits, monitor activity, and can remotely pause playback.

2. **Backend** (Supabase) — PostgreSQL database, auth, realtime pub/sub. Stores parent accounts, approved playlists, cached video metadata, time settings, device pairings, session state, watch history.

3. **Android TV App** (Kotlin + Jetpack Compose) — kid-mode ONLY. Shows approved videos in a playful grid. Plays videos in a locked-down WebView. Enforces time limits. Has no settings, no PIN, no parent mode, no way to access anything unapproved.

### How It Works

**Parent setup (one-time, ~5 minutes on their phone):**
1. Go to kidswatch.app → Sign in with Google (requesting `youtube.readonly` scope)
2. Click "Add a TV" → TV app shows a 4-digit pairing code → parent enters it on the website
3. One-time YouTube sign-in on the TV: after pairing, the TV WebView opens Google sign-in so the parent can authenticate for YouTube Premium ad-free playback. This is the only time the parent touches the TV. Investigate whether we can use the Android TV's existing OS-level Google account instead.
4. Back on the dashboard: parent sees their YouTube playlists (including saved/followed playlists they don't own) → toggles on the ones kids can watch
5. Sets daily time limit (15 min – 3 hours)
6. Done. TV starts showing approved content.

**Parent ongoing (zero effort for content):**
- Browse YouTube normally on any device → save a video to an approved playlist → it appears on the TV automatically at next sync.
- Open dashboard to: adjust time limits, view activity (X of Y videos watched per playlist, daily/weekly watch time), trigger manual sync.
- Future v2: remote pause/resume, time-of-day windows, per-device limits.

**Kid experience (Android TV):**
- App launches to a playful, light-themed grid of video thumbnails organized by playlist rows (horizontal scrolling, Netflix-style but for kids).
- D-pad remote navigation: up/down between playlists, left/right to scroll, OK to play.
- Tap a video → plays fullscreen in a locked WebView (loads only that specific YouTube URL, never shows YouTube's search/recommendations/sidebar/comments/end-screen suggestions).
- Video ends → "Up Next" card for 8 seconds → auto-advances to next video in playlist.
- When all videos in a playlist are watched, loop back to the beginning.
- Time warnings: gentle banners at 10 min, 5 min, 1 min remaining. Time's up: video fades out, friendly animated "All done for today!" screen with no dismiss option.
- YouTube Shorts (videos < 60 seconds) are automatically filtered out and never shown.
- "Not viewing time" screen if outside allowed hours (v2).
- If no network: friendly error screen before YouTube's own error page can appear.

### Technical Architecture Details

**Parent Dashboard (Next.js):**
- Supabase Auth with Google OAuth (youtube.readonly scope)
- YouTube Data API v3 calls from Next.js API routes (server-side) using parent's OAuth token
- Playlist sync: fetches playlistItems for each approved playlist, caches video metadata (title, thumbnail, duration, position) in Supabase
- Sync trigger: manual "Sync Now" button, auto-sync on dashboard load, AND investigate having the TV app trigger sync via Supabase Edge Function on launch
- TV pairing: generate short-lived 4-digit codes stored in Supabase, link device to parent account on code entry
- Activity display: read watch history and session data from Supabase
- Remote controls (v2): update session commands via Supabase, TV subscribes to realtime changes

**Backend (Supabase):**
- PostgreSQL with Row Level Security
- Tables: parents, devices (paired TVs), approved_playlists, cached_videos, time_settings, daily_usage, active_sessions, watch_history
- Realtime: for live session status and remote commands (v2)
- Edge Functions: possibly for TV-triggered playlist sync and health checks
- Encrypted storage: Google refresh tokens
- Time enforcement should be server-authoritative (daily usage tracked in Supabase, not just on-device) so clearing app data doesn't reset limits

**Android TV App (Kotlin + Jetpack Compose for TV):**
- Kid-mode ONLY. No settings, no parent mode, no PIN entry.
- First launch: show pairing code screen. After pairing: show content grid.
- Compose for TV: use `androidx.tv:tv-material` library. D-pad focus management. Large cards (240x135dp+), large text (20sp+). Account for TV overscan (5% safe margins).
- Local cache: Room DB for playlists and video metadata. Coil for aggressive thumbnail disk caching. Show cached content instantly on launch, sync in background.
- Video playback: WebView loads `youtube.com/watch?v={id}` (not embed URL, to ensure Premium works). WebViewClient blocks ALL non-YouTube domains. JavaScript injection hides YouTube UI elements (search, recommendations, sidebar, comments, end screens, channel links, Home button). WebView navigation intercepted: any URL not matching an approved video ID is blocked.
- Allowed WebView domains: youtube.com, *.youtube.com, *.ytimg.com, *.googlevideo.com, accounts.google.com (for initial sign-in only), *.gstatic.com.
- YouTube Premium session: stored in WebView cookie jar after one-time parent sign-in. Health check on launch to verify session is still valid. If expired, show "Parent needed" screen and notify dashboard.
- Timer: fetch daily allowance from Supabase on launch. Client-side countdown, synced with server every 60 seconds. Timer runs during video playback only (pauses on grid/pause). Gentle warnings at 10/5/1 min. "Time's Up" screen cannot be dismissed.
- App lockdown: immersive mode (hide status/nav bars), back button intercepted (does nothing on grid, returns to grid from player), Home button — cannot be intercepted on most Android TV devices (accepted limitation; recommend custom launcher for dedicated kid devices).
- Crash recovery: persist app state in SharedPreferences, relaunch into kid mode on crash.
- Sync: on launch + manual refresh button on grid (pull-to-refresh equivalent for TV, maybe a "Refresh" tile or button accessible via remote). Consider TV app calling Supabase Edge Function to trigger playlist sync using parent's stored OAuth token.

### Design Language (Kid UI on TV)

Light, playful, warm. NOT dark mode.
- Background: soft gradient (warm cream #FFF8F0 → soft peach)
- Cards: white with soft shadows, rounded corners (16dp+), subtle random tilt (1-2°) for playfulness, spring scale animation on focus
- Colors: soft coral (#FF8A80), sky blue (#82B1FF), sunny yellow (#FFE57F), mint green (#A5D6A7)
- Typography: rounded sans-serif (Nunito or Quicksand), minimum 20sp body, 28sp+ headings
- Decorative: small stars, clouds, rainbows in margins — subtle, not distracting
- Video cards: large thumbnail, title (truncated), duration badge, "NEW" sparkle for videos added < 24 hours ago
- Playlist rows: emoji + name as header
- Time remaining: friendly clock icon + minutes in corner
- Loading: bouncing animal character animation
- Errors: friendly illustration (cloud with rain for no network, sleeping character for not viewing time)

### Content Rules

- YouTube Shorts filtered out (duration < 60 seconds AND/OR vertical aspect ratio)
- Videos play in playlist order (as ordered on YouTube)
- When playlist is fully watched, loop to beginning
- Parent dashboard shows progress: "8 of 23 videos watched" per playlist
- Support playlists the parent owns AND playlists they've saved/followed from other creators
- Only one parent Google account per setup (family members add to same playlists)
- v1: one set of playlists and time limits shared across all paired TVs. v2: per-device settings.

### Critical Technical Risks to Address in PRD

1. **WebView compatibility on Android TV** — Fire TV Stick uses Amazon's forked WebView. Cheap TV boxes have outdated WebViews. YouTube player may break. Need device compatibility testing strategy and minimum WebView version requirement.

2. **YouTube Premium in WebView** — must work ad-free. Test: does signing into Google in the WebView and then loading youtube.com/watch?v=X play without ads on Premium accounts? Investigate using the TV's OS-level Google account as an alternative to in-WebView sign-in.

3. **CSS/JS injection fragility** — hiding YouTube UI elements by injecting CSS/JS that targets YouTube's DOM classes. YouTube changes their DOM regularly. Need a strategy for maintaining these selectors (version-check on launch? remote config for CSS overrides?).

4. **Cookie persistence** — WebView cookies can be cleared by OS updates, WebView updates, or storage pressure. Need proactive session health checks, not silent degradation to ad-supported playback.

5. **Google OAuth token lifecycle** — refresh tokens can be revoked. Need graceful re-auth flow that notifies parent via dashboard.

6. **Android TV Home button** — cannot be intercepted. Kid can press Home and reach the TV launcher. Accepted limitation for v1. Document custom launcher setup for dedicated devices.

7. **YouTube API quota** — 10K units/day default. Sync costs ~10 units per cycle. Search costs 100 units. Fine for personal use but needs quota increase application before public launch.

### Scope

**v1 (MVP):**
- Parent web dashboard: Google sign-in, TV pairing, playlist selection, daily time limit, sync, basic activity view
- Android TV app: pairing, content grid, video playback in locked WebView, timer with warnings, Time's Up screen
- Supabase backend: auth, data storage, device pairing

**v2:**
- Remote pause/resume (Supabase Realtime)
- Time-of-day windows (weekday/weekend schedules)
- Per-device time limits
- Activity dashboard with charts
- Multiple kid profiles with different playlists
- Playlist auto-sync via scheduled Edge Function (no manual trigger needed)

**v3:**
- Tablet support (touch-optimized UI)
- Custom launcher option for dedicated devices
- Chromecast support
- "Suggest a video" — kid presses button, parent gets notification
- Bedtime wind-down mode

### Non-Goals
- No iOS app (v1)
- No content that isn't from YouTube
- No video downloading/offline playback
- No ads, no tracking, no analytics beyond what parents see in their own dashboard
- No social features
- No algorithmic recommendations — parents curate everything

Please produce a detailed PRD covering: architecture, data model (Supabase schema), all user flows, API routes, the Android TV app structure (Kotlin project layout, key classes), the WebView lockdown strategy, the pairing flow, time enforcement, build order (phased, with each step producing a testable increment), and known risks with mitigations. The PRD should be detailed enough to hand to Claude Code for implementation.
