# KidsWatch V0.2 — Product Spec

*Updated after Round 3+4 PM + Dev Manager review*

## What It Is

A parent-controlled YouTube viewing experience for kids on Android TV. Parents paste YouTube playlist URLs on a website. The videos from those playlists — and nothing else — appear on the TV. When a parent adds a playlist, it appears on the TV instantly.

No search. No recommendations. No rabbit holes.

---

## Architecture Principle

**The backend is a dumb data store — for now.** Firebase Firestore stores accounts, playlist IDs, device pairings, and play events. Both the parent website and the TV app talk directly to Firestore via SDKs. No custom API server, no REST endpoints, no cron jobs.

Firebase is chosen for future needs:
- **Cloud Functions** for analytics crunching on play event ingest (V0.3)
- **FCM** for pushing time-control messages to the TV (V0.3+)
- **Official Android SDK** for the TV app

The backend never talks to YouTube. All YouTube interaction happens on the edges.

---

## The Parent Journey

1. Parent sideloads the KidsWatch APK onto their Android TV
2. TV shows a pairing code: **"Go to kidswatch.app, enter code ABCD-1234"**
3. Parent opens kidswatch.app on their phone
4. Taps **"Sign in with Google"** (one tap — every YouTube parent has a Google account)
5. Enters the TV code → TV instantly shows "Paired!"
6. Adds a YouTube playlist URL on the website
7. TV instantly shows the new playlist row
8. Kid picks a video and watches

Steps 1-5 are one-time setup. Steps 6-7 are the ongoing loop.

---

## Two Surfaces

### 1. Parent Website

**Purpose:** Parents sign in with Google, add playlists, pair their TV.

**Auth:** Google sign-in only (one button, no forms). Firebase Auth with Google provider. First sign-in auto-creates the family document with display name, email, and photo URL.

**Dashboard (single page, mobile-first):**
- "Your Playlists" — list of added playlists with titles + remove button
- "Add Playlist" — paste URL → browser validates via YouTube oEmbed → shows title → confirm → saves to Firestore
- "Pair a TV" — input for the code displayed on the TV screen
- List of paired TVs (pairing codes)

**Debug page** (`/debug` or `?debug=1`):
- Raw Firestore state: family doc, playlist docs, device docs
- Useful for verifying operations without opening Firebase console

**Mobile-first design:** Most parents will open this on their phone while looking at the TV. Big buttons, stacked layout, thumb-reachable.

### 2. Android TV App

**Purpose:** Kids watch videos from parent-approved playlists. Nothing else.

#### First Run (Pairing)

1. App launches (no local state)
2. `Firebase.auth.signInAnonymously()` → persistent UID
3. Registers FCM token in Firestore (stored for V0.3 push, not used yet)
4. Generates random pairing code (`ABCD-1234`: 4 letters + 4 digits)
5. Collision check against Firestore → regenerate if taken
6. Creates `tv_devices/{uid}` document with `family_id: null`
7. Displays pairing screen: **"Go to kidswatch.app"** + **"ABCD-1234"**
8. Firestore realtime listener on own document
9. `family_id` becomes non-null → "Paired!" → home screen
10. Code expires after 1 hour → auto-regenerate + update display

#### App Restart (Already Paired)

1. Persisted anonymous auth → sign in
2. Query own device doc → `family_id` is set → home screen
3. If `family_id` null → pairing screen

#### Home Screen

- One horizontal row per playlist (Leanback-style)
- Thumbnails (Coil + YouTube CDN `i.ytimg.com`), titles, duration badges
- D-pad: left/right within row, up/down between rows
- **"Refresh Videos" button** (top corner) — forces NewPipe re-resolution of all playlists
- Firestore realtime listener — playlist list updates instantly when parent adds/removes
- Empty state: "All set! Add playlists at kidswatch.app to start watching."
- Loading state: shimmer skeletons while resolving
- **Version overlay** (debug builds): `v0.2.0-debug` in corner of every screen

#### Playlist Resolution + Caching

- On launch: resolve all playlists via NewPipe `PlaylistExtractor` (parallel, IO dispatcher)
- Per video: ID, title, thumbnail URL, duration, position
- Cache to local storage (Room or JSON), keyed by playlist ID
- Cache replaced on every successful resolution
- Network fail → use last-known cache, show "Offline" badge
- New playlist from Firestore listener → resolve immediately

#### Playback

- Video selected → NewPipe `StreamExtractor` on IO dispatcher
- Stream priority: 1080p progressive > 720p progressive > any progressive > adaptive merge
- ExoPlayer fullscreen, built-in D-pad controls
- Auto-advance to next video in playlist; playlist ends → home screen
- Extraction fail → "Can't play this video" → auto-skip 3s
- D-pad: enter = play/pause, left/right = seek, back = home

#### Play Event Recording

- Local Room DB: `{videoId, playlistId, startTime, durationWatched, completedPercent}`
- Batch flush to Firestore every 5 min or on app pause/stop
- Unreachable → queue locally, flush later
- Fire-and-forget, never blocks playback

#### Settings Screen (No PIN — V0.2)

- Gear icon on home screen, open access
- **General:**
  - Refresh Videos (same as home screen button)
  - About / version
- **Debug panel:**
  - **Reset Pairing (Full Reset)** — deletes anonymous auth, device doc, all local state. Back to first-run. Tests the complete pairing flow from scratch.
  - **Unpair (Keep Identity)** — sets `family_id` to null in Firestore, returns to pairing screen. Tests re-activation without regenerating everything.
  - **State Inspector** — device UID, family ID, pairing code, FCM token, playlist count, cached video count, last resolution timestamp per playlist
  - **Resolve Single Playlist** — paste a playlist ID, see resolution results. Tests NewPipe without backend (standalone debug mode).
  - **Stream Inspector** — on video selection, shows all available streams (resolution, codec, bitrate, progressive/adaptive) before playing. Replaces feasibility Test 6.
  - **Play Events** — pending count, "Force Flush" button, "Clear Events" button
  - **Log Panel** — scrollable real-time log of all operations (Firestore calls, NewPipe resolutions, ExoPlayer states, errors). Toggle on/off.
  - **Simulate Offline** — toggle that disconnects Firestore and blocks NewPipe. Tests all offline/cache fallback paths without touching the emulator's network.

#### ADB Debug Intents

For emulator testing without D-pad navigation to Settings:
```
adb shell am broadcast -a com.kidswatch.tv.DEBUG_RESET_PAIRING
adb shell am broadcast -a com.kidswatch.tv.DEBUG_REFRESH_PLAYLISTS
adb shell am broadcast -a com.kidswatch.tv.DEBUG_FORCE_FLUSH_EVENTS
adb shell am broadcast -a com.kidswatch.tv.DEBUG_SIMULATE_OFFLINE
```

#### Error States

| Situation | Behavior |
|-----------|----------|
| No internet, no cache | "Connect to the internet to load your videos" |
| No internet, has cache | Use cache, "Offline" badge |
| Playlist deleted on YouTube | Row: "Playlist no longer available" |
| Video extraction fails | "Can't play this video" → skip 3s |
| Firestore unreachable | Use cache, queue events locally |
| Pairing code expired | Auto-regenerate new code |
| Paired but no playlists | "Add playlists at kidswatch.app" |

---

## Backend (Firebase)

**Services:**
- **Firebase Auth** — Google sign-in for website, anonymous auth for TV
- **Cloud Firestore** — all data, realtime listeners
- **FCM** — token registered in V0.2, push in V0.3+
- **Cloud Functions** — V0.3 for analytics aggregation

### Firestore Collections

```
families/{familyId}                         (familyId = Google Auth UID)
  display_name: string
  email: string
  photo_url: string
  created_at: timestamp

families/{familyId}/playlists/{playlistId}
  youtube_pl_id: string
  display_name: string
  added_at: timestamp

tv_devices/{deviceDocId}                    (deviceDocId = anonymous auth UID)
  device_uid: string
  pairing_code: string
  fcm_token: string
  family_id: string | null
  created_at: timestamp
  paired_at: timestamp | null

play_events/{eventId}
  device_uid: string
  family_id: string
  video_id: string
  playlist_id: string
  started_at: timestamp
  duration_sec: number
  completed_pct: number
```

### Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /families/{familyId} {
      allow read, write: if request.auth != null && request.auth.uid == familyId;

      match /playlists/{playlistId} {
        // Owner can CRUD
        allow read, write: if request.auth != null && request.auth.uid == familyId;
        // Paired TV can read
        allow read: if request.auth != null
          && exists(/databases/$(database)/documents/tv_devices/$(request.auth.uid))
          && get(/databases/$(database)/documents/tv_devices/$(request.auth.uid)).data.family_id == familyId;
      }
    }

    match /tv_devices/{deviceDocId} {
      // TV can create its own doc (anonymous auth)
      allow create: if request.auth != null
        && request.resource.data.family_id == null
        && request.resource.data.device_uid == request.auth.uid;
      // TV can read its own doc
      allow read: if request.auth != null
        && resource.data.device_uid == request.auth.uid;
      // TV can update its own doc (for re-pairing, FCM token refresh)
      allow update: if request.auth != null
        && resource.data.device_uid == request.auth.uid;
      // Parent can query by pairing_code and activate (set family_id)
      allow list: if request.auth != null;
      allow update: if request.auth != null
        && resource.data.family_id == null
        && request.resource.data.family_id == request.auth.uid;
    }

    match /play_events/{eventId} {
      allow create: if request.auth != null;
    }
  }
}
```

### Firestore Operations

**Parent website (Firebase JS SDK):**
```
Sign in:        signInWithPopup(googleProvider) or signInWithRedirect(googleProvider)
                → on first sign-in: set("families/{uid}", { display_name, email, photo_url, created_at }, { merge: true })

Add playlist:   families/{uid}/playlists.add({ youtube_pl_id, display_name, added_at })

Remove:         families/{uid}/playlists/{id}.delete()

List:           families/{uid}/playlists.onSnapshot(...)   // realtime

Activate TV:    tv_devices.where("pairing_code", "==", code)
                  .where("family_id", "==", null)
                  .where("created_at", ">", oneHourAgo).get()
                → deviceDoc.update({ family_id: uid, paired_at: now })
```

**TV app (Firebase Android SDK):**
```
First launch:   Firebase.auth.signInAnonymously() → uid

Register:       tv_devices/{uid}.set({ device_uid: uid, pairing_code, fcm_token, family_id: null, created_at })

Listen pairing: tv_devices/{uid}.addSnapshotListener → when family_id != null → paired

Get playlists:  families/{familyId}/playlists.addSnapshotListener  // realtime

Write events:   play_events.add({ ... })   // batched from local queue

Reset pairing:  tv_devices/{uid}.delete() + Firebase.auth.signOut() + clear SharedPreferences

Unpair:         tv_devices/{uid}.update({ family_id: null, paired_at: null })
```

---

## Data Flow

```
PAIRING:
TV app                         Firestore                    Parent website
  |-- signInAnonymously() ----->|                              |
  |<-- uid --------------------|                              |
  |-- create tv_devices/{uid} ->|                              |
  |   { code: ABCD-1234 }      |                              |
  |-- listen tv_devices/{uid} ->|                              |
  |                              |                              |
  |   display: "ABCD-1234       |                              |
  |   at kidswatch.app"         |                              |
  |                              |<-- signInWithGoogle ---------|
  |                              |<-- query by code, activate --|
  |                              |    update family_id          |
  |<-- snapshot: paired! -------|                              |
  |   → home screen             |                              |

NORMAL USE:
TV app                         Firestore                    YouTube
  |-- listen playlists -------->|                              |
  |<-- realtime updates --------|                              |
  |                              |                              |
  |-- NewPipe resolve playlist --|------------------------------>|
  |<-- video list + metadata ---|-------------------------------|
  |                              |                              |
  |-- NewPipe extract stream ----|------------------------------>|
  |<-- stream URL ---------------|-------------------------------|
  |   ExoPlayer plays            |                              |
  |                              |                              |
  |-- batch play events -------->|                              |
```

---

## What's NOT in V0.2

- Individual video curation (playlist-only)
- Multiple parent accounts per family
- Time limits or scheduling (V0.3 — FCM registered now)
- Parent PIN on TV (V0.3 — alongside time controls)
- Offline playback / download
- Content filtering or safety scanning
- YouTube Premium integration
- Casting or multi-device sync
- iOS or non-TV Android app
- Private/unlisted playlists (public only)
- Server-side YouTube interaction
- Video list preview on website
- Play Store distribution (sideload only)
- **Analytics dashboard** (V0.3)
- **Push messages to TV** (V0.3)
- **Rate limiting** (V0.4)

---

## Key Technical Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Parent auth | Google sign-in only | Every YouTube parent has Google. One tap. No forms |
| TV auth | Firebase Anonymous Auth | Persistent UID, proper security, FCM token |
| Backend | Firebase (Firestore + Auth + FCM) | Cloud Functions + FCM for V0.3. Official Android SDK |
| API style | Direct Firestore SDK | No custom server. Security rules for auth |
| Playlist sync | Firestore realtime listener | Instant updates, no polling |
| Playlist resolution | TV-side NewPipe | Decoupled from backend. Website only validates via oEmbed |
| Cache | Resolve on launch + "Refresh Videos" button, stale-if-offline | Simple, debuggable |
| Stream quality | Highest progressive up to 1080p, adaptive fallback | TV needs 720p+ |
| Play tracking | Local queue → batched Firestore writes | Fire-and-forget |
| Playback | ExoPlayer (Media3) | Native, D-pad, proven |
| TV UI | Leanback rows | Standard Android TV |
| Debug | Full debug panel + ADB intents | Fast iteration on emulator |
| Distribution | Sideload APK | No Play Store until public launch |

---

## Prerequisites

### Test 5: Playlist Resolution
Validate NewPipe `PlaylistExtractor`: public playlists at 10/50/200 videos, metadata completeness, pagination, error on deleted/private. Exit criteria: 50 videos in <10s.

### Test 6: Stream Quality
Validate 720p/1080p progressive and adaptive merge on TV emulator. (Can fold into TV app's Stream Inspector debug tool.)

**Phase 0 is go/no-go.**

---

## Open Questions

1. **Playlist limits** — Max per family? Suggest 10 for V0.2
2. **Multiple TVs** — Schema supports it. Allow in V0.2?
3. **Private playlists** — Future. Requires YouTube OAuth.
