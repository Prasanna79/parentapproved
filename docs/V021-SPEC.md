# KidsWatch V0.2.1 — Product Spec (Local Server Architecture)

*Replaces the Firebase-based V0.2 spec. The TV app is the entire backend.*

## What It Is

A parent-controlled YouTube viewing experience for kids on Android TV. Parents open their phone browser, connect to the TV on their home WiFi, and paste YouTube playlist URLs. The videos from those playlists — and nothing else — appear on the TV.

No search. No recommendations. No rabbit holes. No cloud. No accounts.

---

## Architecture Principle

**The TV is the backend.** The TV app runs an embedded HTTP server (Ktor). The parent connects to it from their phone's browser on the same WiFi network. All data lives in a local Room database on the TV. No Firebase, no cloud, no external services.

YouTube interaction happens on the TV only, via NewPipe. The parent's phone never talks to YouTube.

**Why this architecture:**
- Zero infrastructure cost, forever
- All data stays on the TV in the parent's living room
- One artifact to ship (an APK)
- Lower YouTube risk profile (no public website, no hosted infrastructure)
- Designed for future remote access via relay (V0.3) without changing the core

---

## The Parent Journey

1. Parent sideloads the KidsWatch APK onto their Android TV
2. TV shows a connect screen: **QR code + PIN + IP address**
3. Parent scans the QR code with their phone camera (or types the IP)
4. Browser opens, parent enters the 6-digit PIN
5. Adds a YouTube playlist URL on the dashboard
6. TV shows the new playlist within seconds (parent taps "Refresh" or waits for next auto-refresh)
7. Kid picks a video and watches

Steps 1-4 are one-time setup per browser session. Steps 5-6 are the ongoing loop.

---

## One Surface, Two Interfaces

### 1. Parent Dashboard (served by TV)

**Purpose:** Parents add playlists, view watch stats, manage the TV. Runs in the parent's phone browser.

**Delivery:** Static HTML/CSS/JS bundled as Android assets in the APK, served by Ktor. No separate web project. No deployment. No hosting.

**Auth:** 6-digit PIN displayed on the TV screen. Parent enters it in the browser. Session cookie persists until browser is closed or PIN changes.

**Security:**
- 6-digit PIN (1,000,000 combinations)
- Rate limit: 5 wrong attempts → 5 minute lockout, exponential backoff
- Lockout notification on TV screen: "Someone tried the wrong PIN 5 times"
- PIN displayed only on TV screen — never stored remotely, never transmitted except during auth
- HTTP on local network (same as routers, printers, NAS devices)

**Dashboard (single page, mobile-first):**
- "Your Playlists" — list of added playlists with titles + remove button
- "Add Playlist" — paste URL → browser validates via YouTube oEmbed → shows title → confirm → saves to Room DB
- Connection status indicator (connected to TV / disconnected)
- "What They Watched" — today's play stats (videos, total time, last watched)

**Design:** Mobile-first. Parent holds phone while looking at TV. Big buttons, stacked layout, thumb-reachable. Works in Chrome and Safari.

### 2. Android TV App

**Purpose:** Kids watch videos from parent-approved playlists. Nothing else.

#### First Run (Connect Screen)

1. App launches (no local state)
2. Ktor embedded server starts on port 8080
3. Generates random 6-digit PIN
4. Detects local IP via `WifiManager`
5. Displays connect screen:
   - QR code encoding `http://<ip>:8080?pin=<pin>`
   - PIN in large text: **"123456"**
   - IP address in small text: `192.168.1.42:8080`
   - Instructions: **"Scan this code with your phone"**
6. PIN regenerates every 24 hours while on connect screen

#### App Restart

1. Check Room DB for playlists
2. Playlists exist → home screen
3. No playlists → connect screen (with current PIN)

#### Home Screen

- One horizontal row per playlist (Leanback-style)
- Thumbnails (Coil + YouTube CDN `i.ytimg.com`), titles, duration badges
- D-pad: left/right within row, up/down between rows
- **"Refresh Videos" button** (top corner) — forces NewPipe re-resolution of all playlists
- Empty state: "Scan the QR code on your phone to add playlists."
- Loading state: shimmer skeletons while resolving
- **Version overlay** (debug builds): `v0.2.1-debug` in corner of every screen

#### Playlist Resolution + Caching

- On launch: resolve all playlists via NewPipe `PlaylistExtractor` (parallel, IO dispatcher)
- Per video: ID, title, thumbnail URL, duration, position
- Cache to Room DB, keyed by playlist ID
- Cache replaced on every successful resolution
- Network fail → use last-known cache, show "Offline" badge
- New playlist added via parent dashboard → resolve immediately on next "Refresh" or auto-resolve interval

#### Playback

- Video selected → NewPipe `StreamExtractor` on IO dispatcher
- Stream priority: 1080p progressive > 720p progressive > any progressive > adaptive merge
- ExoPlayer fullscreen, built-in D-pad controls
- Auto-advance to next video in playlist; playlist ends → home screen
- Extraction fail → "Can't play this video" → auto-skip 3s
- D-pad: enter = play/pause, left/right = seek, back = home

#### Play Event Recording

- Room DB: `{videoId, playlistId, startTime, durationWatched, completedPercent}`
- Stored locally only — queryable from parent dashboard via Ktor `/stats` endpoint
- Never leaves the device

#### Settings Screen (No PIN Gate — V0.2.1)

- Gear icon on home screen, open access
- **General:**
  - Refresh Videos (same as home screen button)
  - About / version
  - Show Connect Info (redisplay QR code + PIN for parent)
- **Debug panel:**
  - **Full Reset** — deletes Room DB, regenerates PIN. Back to first-run.
  - **State Inspector** — PIN, IP address, playlist count, cached video count, last resolution timestamp per playlist, play event count
  - **Resolve Single Playlist** — paste a playlist ID, see resolution results. Tests NewPipe standalone.
  - **Stream Inspector** — on video selection, shows all available streams (resolution, codec, bitrate, progressive/adaptive) before playing.
  - **Play Events** — count, "Clear Events" button
  - **Log Panel** — scrollable real-time log of all operations (Ktor requests, NewPipe resolutions, ExoPlayer states, errors). Toggle on/off.
  - **Simulate Offline** — toggle that blocks NewPipe network calls. Tests cache fallback paths.

#### ADB Debug Intents

For emulator testing without D-pad navigation:
```
adb shell am broadcast -a com.kidswatch.tv.DEBUG_FULL_RESET
adb shell am broadcast -a com.kidswatch.tv.DEBUG_REFRESH_PLAYLISTS
adb shell am broadcast -a com.kidswatch.tv.DEBUG_SHOW_CONNECT_INFO
adb shell am broadcast -a com.kidswatch.tv.DEBUG_SIMULATE_OFFLINE
```

#### Error States

| Situation | Behavior |
|-----------|----------|
| No internet, no cache | "Connect to the internet to load your videos" |
| No internet, has cache | Use cache, "Offline" badge |
| Playlist deleted on YouTube | Row: "Playlist no longer available" |
| Video extraction fails | "Can't play this video" → skip 3s |
| Parent can't reach TV | Connect screen shows current IP. Check same WiFi. |
| No playlists added | "Scan the QR code on your phone to add playlists." |
| WiFi IP changes | Connect screen updates automatically. New QR code. |

---

## Embedded Server (Ktor)

**Framework:** Ktor (Kotlin-native, coroutines, runs on Android)

**Port:** 8080 (configurable in debug)

**Lifecycle:** Runs while the app is in the foreground. Stops when app is backgrounded or TV goes to sleep. This is fine — the parent configures while looking at the TV.

### API Routes

```
GET  /                          → serves parent dashboard (index.html)
GET  /assets/*                  → serves CSS, JS, images from APK assets

POST /auth                      → { pin: "123456" } → 200 + session cookie | 401
                                  Rate-limited: 5 fails → 429 for 5 min

GET  /playlists                 → list all playlists (requires session)
POST /playlists                 → { url: "https://youtube.com/playlist?list=..." }
                                  → validates via oEmbed → saves to Room → 201
DELETE /playlists/:id           → removes playlist → 200

GET  /stats                     → play events summary (today, this week)
GET  /stats/recent              → last 20 videos watched

GET  /status                    → { paired: true, playlists: 3, cached_videos: 47 }
```

All routes except `/`, `/assets/*`, and `/auth` require a valid session cookie.

### Session Management

- Session = random 32-byte token, stored in memory (HashMap)
- Set as `HttpOnly` cookie on successful PIN auth
- Sessions expire after 24 hours or when PIN changes
- Max 5 concurrent sessions (parent's phone, tablet, laptop)

---

## Local Data (Room DB)

```
playlists
  id: auto-increment
  youtube_pl_id: string
  display_name: string
  added_at: timestamp

cached_videos
  id: auto-increment
  playlist_id: FK → playlists.id
  video_id: string
  title: string
  thumbnail_url: string
  duration_sec: int
  position: int
  resolved_at: timestamp

play_events
  id: auto-increment
  video_id: string
  playlist_id: FK → playlists.id
  started_at: timestamp
  duration_watched_sec: int
  completed_pct: float
```

---

## Data Flow

```
SETUP:
Parent's phone                     Android TV (Ktor + Room)         YouTube
     |                                      |                         |
     |  scan QR code                        |                         |
     |-- GET / --------------------------> |                         |
     |<-- dashboard HTML -----------------|                         |
     |-- POST /auth { pin } -------------> |                         |
     |<-- 200 + session cookie ------------|                         |
     |                                      |                         |
     |-- POST /playlists { url } ---------> |                         |
     |                                      |-- oEmbed validate ----> |
     |                                      |<-- title --------------|
     |                                      |-- save to Room          |
     |<-- 201 { playlist } ---------------|                         |

NORMAL USE:
Kid (D-pad)                        Android TV                       YouTube
     |                                      |                         |
     |  (app launch)                        |                         |
     |                                      |-- NewPipe resolve ----> |
     |                                      |<-- video metadata -----|
     |                                      |-- cache to Room         |
     |                                      |                         |
     |-- select video ------------------->  |                         |
     |                                      |-- NewPipe extract ----> |
     |                                      |<-- stream URL ----------|
     |                                      |-- ExoPlayer plays       |
     |                                      |-- log play event (Room) |

STATS:
Parent's phone                     Android TV
     |                                      |
     |-- GET /stats ----------------------> |
     |<-- { today: 3 videos, 45 min } ----|
```

---

## What's NOT in V0.2.1

- Remote access from outside the house (V0.3 — relay)
- Individual video curation (playlist-only)
- Multiple TVs with shared playlists
- Time limits or scheduling
- Offline playback / download
- Content filtering or safety scanning
- YouTube Premium integration
- iOS or non-TV Android app
- Private/unlisted playlists (public only)
- Play Store distribution (sideload only)
- Push notifications to parent
- Auto-refresh on playlist add (parent taps "Refresh" or waits)

---

## Key Technical Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Architecture | TV-hosted server, no cloud | Charityware. Zero cost. Full privacy. One artifact. |
| Parent interface | Phone browser → TV's Ktor server | No app install. No account. Works immediately. |
| Auth | 6-digit PIN on TV screen | Simple. Visual. No Google account needed. |
| Server | Ktor (embedded) | Kotlin-native, coroutines, clean routing, runs on Android |
| Data store | Room DB | Single source of truth. Already needed for video cache. |
| Parent UI | Static HTML/JS in APK assets | No build step. No deployment. Ships with the APK. |
| Playlist validation | YouTube oEmbed | Lightweight check. No API key. Returns title. |
| Playlist resolution | TV-side NewPipe | Same as V0.1 feasibility. Proven. |
| Cache | Resolve on launch + "Refresh" button | Simple, debuggable |
| Stream quality | Highest progressive up to 1080p, adaptive fallback | TV needs 720p+ |
| Play tracking | Room DB only (local) | Privacy. No cloud. Parent queries via dashboard. |
| Playback | ExoPlayer (Media3) | Native, D-pad, proven |
| TV UI | Leanback rows | Standard Android TV |
| Security | PIN + rate limiting | Appropriate for local network threat model |
| Distribution | Sideload APK | Charityware. GitHub releases. |

---

## Prerequisites

### Test 5: Playlist Resolution
Validate NewPipe `PlaylistExtractor`: public playlists at 10/50/200 videos, metadata completeness, pagination, error on deleted/private. Exit criteria: 50 videos in <10s.

### Test 6: Stream Quality
Validate 720p/1080p progressive and adaptive merge on TV emulator.

### Test 7: Ktor on Android TV
Validate Ktor embedded server runs on Android TV emulator. Serve a test page, accept POST, verify from phone browser on same network. Test sleep/wake behavior.

### Test 8: QR Code Generation
Validate ZXing QR code generation renders correctly on TV. Test scanning from iPhone and Android phones.

**Tests 5-8 are go/no-go for V0.2.1.**

---

## Open Questions

1. **Playlist limits** — Max per TV? Suggest 20 for V0.2.1
2. **Auto-refresh interval** — Resolve playlists every N hours automatically? Or only on manual "Refresh"?
3. **Server lifecycle** — Foreground-only (simpler) or foreground service (survives backgrounding)? Recommend foreground-only for V0.2.1.
4. **oEmbed for validation** — oEmbed returns title but not video count. Is title-only confirmation enough for the parent?
