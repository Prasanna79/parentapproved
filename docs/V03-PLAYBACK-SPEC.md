# KidsWatch V0.3 — Product Spec: "The Remote"

*Parents control playback from their phone. Kids control it from the TV remote.*

---

## Problem

V0.2 plays videos but the user can't interact with playback. The D-pad does nothing during video playback. Parents can't see what's currently playing or stop it from their phone. The playlist shows as a raw YouTube ID instead of a name.

---

## What's In v0.3

### 1. D-pad Playback Controls (P0)

Kids need to control video playback using the TV remote.

| Remote Button | Action |
|--------------|--------|
| D-pad Center | Play/pause toggle |
| D-pad Right | Next video in playlist |
| D-pad Left | Previous video (or restart current if >5s in) |
| Back | Return to home screen |

ExoPlayer's `PlayerView` already supports play/pause and seeking. The issue is that the Compose `AndroidView` wrapper isn't forwarding key events. Fix: request focus and handle D-pad key events.

The built-in ExoPlayer transport controls (seek bar, play/pause icon) should appear when any D-pad button is pressed, then auto-hide after 3 seconds of inactivity.

### 2. Now Playing on Dashboard (P0)

The phone dashboard shows a live "Now Playing" card at the top:

```
┌─────────────────────────────────┐
│  ▶ Now Playing                  │
│  Lego Ninjago S1 Episode 3      │
│  4:32 / 11:00                   │
│                                 │
│  [ Stop ]    [ Next ]           │
└─────────────────────────────────┘
```

- Polls `GET /status` every 5 seconds
- When nothing is playing, shows "Nothing playing right now"
- Stop button sends `POST /playback/stop`
- Next button sends `POST /playback/skip`

### 3. Playback Control API (P0)

New Ktor endpoints (authenticated):

| Method | Path | Action |
|--------|------|--------|
| POST | `/playback/stop` | Stop playback, navigate to home |
| POST | `/playback/skip` | Skip to next video in playlist |
| GET | `/status` | (existing) Add video title, elapsed, duration |

The `/status` response shape becomes:

```json
{
  "serverRunning": true,
  "nowPlaying": {
    "videoId": "abc123",
    "title": "Lego Ninjago S1 Episode 3",
    "playlistId": "PLxxx",
    "playlistTitle": "Lego Ninjago Season 1",
    "elapsed": 272,
    "duration": 660,
    "playing": true
  }
}
```

### 4. Playlist Display Names (P1)

When a playlist is resolved via NewPipe, the playlist title is extracted and stored in the `PlaylistEntity.displayName` field. Shown on:
- TV home screen (row header)
- Phone dashboard (playlist list)
- Now Playing card (playlist context)

`GET /playlists` response includes `displayName`.

### 5. App Launcher Banner (P2)

Android TV requires a 320x180 banner image for the app launcher. Create a simple banner with the KidsWatch name and a playful icon.

Add to `AndroidManifest.xml`:
```xml
android:banner="@drawable/banner"
```

### 6. Overscan Safe Padding (P2)

Add 48dp padding on all edges of the root layout to account for TV overscan. Standard Android TV safe area. Affects HomeScreen, ConnectScreen, SettingsScreen. PlaybackScreen is full-bleed (video should fill the screen).

---

## What's NOT in v0.3

- Kiosk mode / kid-proofing (v0.5)
- Remote relay / access outside WiFi (v0.4)
- Time limits / parental controls (v0.5)
- Volume control from phone
- Video-level selection from phone (pick specific video to play)
- WebSocket for real-time updates (polling is fine)

---

## How It Connects

The playback control commands (stop, skip) flow through the same Ktor server. When we add the relay in v0.4, these endpoints work identically over the WebSocket tunnel. No code changes needed on the TV side.

```
v0.3: Phone ──HTTP──► TV Ktor ──► ExoPlayer
v0.4: Phone ──HTTPS──► Relay ──WS──► TV Ktor ──► ExoPlayer
```

---

## Success Criteria

1. Kid presses play/pause on remote — video pauses/resumes
2. Kid presses right on remote — next video plays
3. Parent opens dashboard — sees "Now Playing" with video title
4. Parent taps Stop — video stops, TV returns to home
5. Parent taps Next — TV skips to next video
6. Playlist shows "Lego Ninjago Season 1" not "PLRNbTEZ7dhL..."
7. App has a visible icon in Android TV launcher
8. No content cut off at screen edges
