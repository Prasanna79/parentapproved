# KidsWatch V0.2 — Detailed Plan

*Final version. Firebase, Google Auth, sideload, debug tools.*

---

## Phase 0: Feasibility Validation

### 0.1 — Test 5: Playlist Resolution
- Add Test 5 to feasibility app
- Test NewPipe `PlaylistExtractor`: 5 / 50 / 200 video playlists, deleted, private
- Measure: time, metadata completeness, pagination
- **Exit:** 50-video playlist in <10s with full metadata

### 0.2 — Test 6: Stream Quality
- Extend Test 4: 720p/1080p progressive + adaptive merge
- Validate on TV emulator
- **Exit:** 720p+ plays reliably

**Go/no-go gate.**

---

## Phase 1: Firebase Backend

### 1.1 — Project Setup
- Create Firebase project
- Enable Auth (Google provider + Anonymous)
- Enable Firestore
- Enable FCM

### 1.2 — Firestore Structure
- `families/{uid}` — auto-created on first Google sign-in
- `families/{uid}/playlists/{id}` — subcollection
- `tv_devices/{uid}` — anonymous auth UID as doc ID
- `play_events/{id}` — write-only

### 1.3 — Security Rules
- Deploy rules per spec
- Test in Firebase emulator: parent CRUD on playlists, TV read playlists, TV create events, pairing activation

### 1.4 — Pairing Flow Verification
- Manual test: anonymous auth → create device → Google auth user activates code → realtime listener fires
- Test code expiry (reject >1 hour old)
- Test collision check

**Deliverable:** Firebase project, all collections, rules verified.

---

## Phase 2: Parent Website

### 2.1 — Setup
- React + Firebase JS SDK (or plain HTML + Firebase SDK for speed)
- Firebase Hosting (one-command deploy)
- Mobile-first responsive design

### 2.2 — Auth
- Single button: "Sign in with Google" (`signInWithPopup`)
- Auto-create family doc on first sign-in (`set` with `merge: true`)
- Show display name + photo in header after sign-in

### 2.3 — Dashboard
- **Playlists:** realtime list from Firestore, add (paste URL → oEmbed validate → save), remove
- **Pair TV:** input code → query `tv_devices` by code (unpaired, <1hr old) → set `family_id`
- **Paired TVs:** list of device codes linked to this family
- **Debug** (`?debug=1`): raw Firestore state dump (family, playlists, devices)

### 2.4 — URL Validation
- Regex extract playlist ID from URL
- YouTube oEmbed for title validation
- Reject: non-playlist URLs, duplicates, private (oEmbed fails)

**Deliverable:** Deployed website where parent can Google sign in, add playlists, pair TV.

---

## Phase 3: Android TV App

### 3.1 — Project Setup
- New project `com.kidswatch.tv`, min SDK 24, target 35, Leanback
- Dependencies: Firebase (Auth, Firestore, FCM, Messaging), NewPipeExtractor, Media3, Coil, Room, desugaring
- Port `NewPipeDownloader` from feasibility app
- `google-services.json` from Firebase console

### 3.2 — Firebase Init
- Anonymous auth on launch
- FCM token registration (stored in device doc)
- Firestore SDK configured

### 3.3 — Pairing Screen
- Generate code (ABCD-1234), collision check, write device doc
- Display: URL + code, large text, dark background
- Realtime listener → `family_id` set → "Paired!" → home
- Auto-regenerate on expiry (1hr)

### 3.4 — Home Screen
- Firestore listener on `families/{familyId}/playlists` (realtime add/remove)
- Per playlist: NewPipe `PlaylistExtractor` on IO, parallel
- Leanback rows: thumbnail + title + duration per video
- Coil for images, disk cache
- "Refresh Videos" button (top corner)
- Empty state, loading shimmer, error per row
- Version overlay in debug builds

### 3.5 — Playlist Cache
- Room DB or JSON, keyed by playlist ID
- Replace on successful resolution, persist across restarts
- Fallback to cache on network failure

### 3.6 — Playback
- StreamExtractor on IO, quality priority: 1080p > 720p > any progressive > adaptive merge
- ExoPlayer fullscreen, auto-advance, D-pad controls
- Error → skip after 3s

### 3.7 — Play Events
- Room table for pending events
- Insert on play start, update on pause/stop/end
- Flush every 5min + onPause/onStop → batch write to Firestore → delete local
- Never blocks UI

### 3.8 — Settings + Debug Panel
**General:**
- Refresh Videos
- About / version

**Debug section:**
- Reset Pairing (full factory reset of app identity)
- Unpair (keep identity, null out family_id)
- State Inspector (UID, family ID, code, FCM token, playlist/video counts, timestamps)
- Resolve Single Playlist (paste ID, test NewPipe standalone)
- Stream Inspector (show all streams for selected video)
- Play Events (pending count, force flush, clear)
- Log Panel (realtime scrollable log of all ops)
- Simulate Offline (toggle Firestore + NewPipe disconnection)

### 3.9 — ADB Debug Intents
Register broadcast receivers for:
```
com.kidswatch.tv.DEBUG_RESET_PAIRING
com.kidswatch.tv.DEBUG_REFRESH_PLAYLISTS
com.kidswatch.tv.DEBUG_FORCE_FLUSH_EVENTS
com.kidswatch.tv.DEBUG_SIMULATE_OFFLINE
```

### 3.10 — Error Handling
Per error state table in spec. Every error → user message, never crash.

---

## Phase 4: Integration & Polish

### 4.1 — End-to-End Flow
1. Sideload → pairing code appears
2. Google sign in on website
3. Enter code → TV shows "Paired!"
4. Empty state shown
5. Add playlist → TV row appears instantly
6. Play video → 720p+, auto-advance
7. Add second playlist → appears without restart
8. Remove playlist → disappears
9. Check Firestore: play_events populated
10. Kill + relaunch → cached data, then refresh
11. Disconnect → offline mode works
12. "Refresh Videos" → re-resolves all
13. ADB intents work (reset, refresh, flush, offline)

### 4.2 — UX Polish
- Focus animation, smooth scrolling
- Consistent 16:9 thumbnails
- Player controls auto-hide
- Pairing screen looks clean
- Debug panel is usable but doesn't pollute kid-facing UX

### 4.3 — Distribution
- Signed release APK
- Share via Drive or direct transfer
- Document: `adb install kidswatch-v0.2.apk`

---

## Milestones

| Phase | What | Depends On |
|-------|------|------------|
| 0 | Feasibility: playlist + quality | Nothing |
| 1 | Firebase setup + rules | Nothing (parallel with 0) |
| 2 | Parent website | Phase 1 |
| 3 | Android TV app | Phase 0 + 1 |
| 4 | Integration + polish | Phase 2 + 3 |

---

## Success Criteria

1. Parent: Google sign in → add 3 playlists → pair TV — under 5 minutes
2. Kid: see + play videos from all playlists using D-pad only
3. 720p+ playback, <5s tap-to-play
4. Auto-advance within playlist
5. Realtime: add playlist on website → shows on TV within seconds
6. "Refresh Videos" re-resolves all playlists
7. Play events in Firestore
8. Handles: no network, deleted playlist, extraction failure — no crashes
9. Debug tools work: reset, unpair, state inspector, log panel, ADB intents
10. Distributable as signed APK
