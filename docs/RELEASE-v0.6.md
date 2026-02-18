# ParentApproved.tv v0.6 "Content Sources" — Release Notes

**Date:** February 18, 2026
**Milestone:** Paste any YouTube URL — videos, channels, playlists, shows. The app figures out the rest.

---

## Any YouTube URL, one input box

v0.5 only accepted playlist URLs (`?list=PL...`). Parents had to know what a "playlist ID" was. v0.6 accepts anything YouTube: a video link, a channel page, a handle (`@PBSKids`), a shorts URL, a show page, even a bare playlist ID pasted from somewhere. The parser recognizes 15+ URL patterns and tells you clearly when something won't work ("Vimeo support coming soon!").

```
Paste any link in —
channels, shorts, a playlist,
the TV just plays.
```

---

## What's New

### Content Sources (any YouTube URL)
- **Videos**: `youtube.com/watch?v=`, `youtu.be/`, `/shorts/`, `/embed/`, `/v/`, `/live/`
- **Playlists**: `?list=PL...`, bare `PLxxxxx` IDs, `/show/VLPL...`
- **Channels**: `/channel/UCxxxxx`, `/@handle`, `/c/name`, `/user/name`
- **Smart rejection**: Vimeo URLs get "coming soon" message. Mix/radio playlists, uploads, liked videos, and watch later are rejected with clear explanation. Private IPs and direct media files blocked.
- **Case-insensitive**: `HTTPS://WWW.YOUTUBE.COM/PLAYLIST?LIST=PLtest` works fine
- **73 URL test fixtures** covering every pattern and edge case

### Source-Agnostic Data Model
- New `channels` table replaces `playlists` — stores `source_type` (yt_playlist, yt_video, yt_channel), `source_id`, `source_url`, `display_name`, `video_count`
- Room database migration v2 → v3 preserves all existing playlist data
- Ready for Vimeo and other sources in future releases
- Up to 200 videos resolved per source (was unlimited — prevents memory issues on large channels)

### Dashboard Updates
- Input placeholder: "Paste any YouTube URL" (was "Paste playlist URL")
- Section heading: "Content Sources" (was "Playlists")
- Source list shows video count: "PBS Kids — 42 videos"
- Recent Activity shows video titles instead of raw video IDs
- Error messages include actionable guidance ("Try pasting a regular playlist URL")

### Auto-Refresh on Startup
- All content sources resolve in the background when the app launches
- Sequential resolution (not parallel) to avoid YouTube rate limiting
- Failed sources fall back to cached videos from last successful resolve

---

## Bug Fixes

- **TV screensaver during playback** — Android TV's Daydream screensaver kicked in after the inactivity timeout even while video was playing. Added `keepScreenOn = true` to PlayerView. One line, should have been there from day one.
- **Remote toggle in wrong place** — The enable/disable relay toggle was in SettingsScreen, but parents first see ConnectScreen (that's where QR + PIN are). Moved the full toggle to ConnectScreen's settings panel. SettingsScreen no longer has a remote access section.
- **Hardcoded app version** — RelayConnector sent `appVersion = "0.5.0"` to the relay regardless of actual version. Now uses `BuildConfig.VERSION_NAME` via constructor injection. The relay always knows what version each TV runs.

---

## Technical Details

### New Files
- `ContentSourceParser.kt` — pure regex URL parser, no network calls at parse time
- `ContentSourceRepository.kt` — replaces PlaylistRepository, dispatches to playlist/video/channel resolvers
- `ChannelEntity.kt` + `ChannelDao.kt` — Room entity and DAO for source-agnostic storage

### Deleted Files
- `PlaylistEntity.kt`, `PlaylistDao.kt`, `PlaylistUrlParser.kt`, `PlaylistRepository.kt` — replaced by the new content source system

### Migration
- Room database v2 → v3: creates `channels` table, migrates playlist data, backfills video titles in `play_events`, drops old `playlists` table
- `play_events` table gains `title` column (default empty string)

---

## Test Coverage

| Suite | Count | Runner |
|-------|-------|--------|
| TV unit tests | 191 | `./gradlew testDebugUnitTest` |
| Relay tests | 139 | `cd relay && npx vitest run` |
| TV instrumented | 19 | `./gradlew connectedDebugAndroidTest` |
| **Total verified** | **349** | |

All tests pass on emulator (TV_API34). Mi Box testing pending.

---

## Known Issues

- **D-pad / emulator controls unresponsive** — navigation doesn't work on TV_API34 emulator. Requires debug intents to trigger playback. Unclear if emulator-only or a regression. Will test on Mi Box.
- **No real-hardware verification yet** — Mi Box was unavailable during this release. All testing emulator-only.

---

## Deferred

- **Vimeo support**: Parser rejects Vimeo URLs with "coming soon" message. Actual Vimeo extraction deferred to a future release.
- **Per-playlist time limits**: Data model supports it, but time controls are a v0.7 feature.
- **Channel thumbnail in dashboard**: Source list shows name + count but no thumbnail. Could add channel avatar later.

---

## Verified Hardware
- **Emulator:** TV_API34 (Android 14, arm64) — all tests pass, D-pad broken
- **Real device:** Xiaomi Mi Box 4 — **pending** (test when back on WiFi)
- **Dashboard:** Not browser-tested this release — pending
