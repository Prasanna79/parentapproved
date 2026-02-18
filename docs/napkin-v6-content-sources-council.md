# v0.6 Content Sources — Council Meeting

**Date:** February 18, 2026
**Feature:** Accept any video URL — YouTube videos, channels, playlists, Vimeo, direct links
**Current state:** Only YouTube playlists (URLs with `?list=PL...`) are accepted

---

## The URL Zoo — What People Actually Paste

```
# YouTube playlist (already works)
https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf

# YouTube single video
https://www.youtube.com/watch?v=dQw4w9WgXcQ
https://youtu.be/dQw4w9WgXcQ
https://m.youtube.com/watch?v=abc123

# YouTube channel
https://www.youtube.com/@PBSKids
https://www.youtube.com/channel/UCkMhivLMAaI-cpNwFPaOYUQ
https://www.youtube.com/c/PBSKids
https://www.youtube.com/user/PBSKids

# YouTube video in a playlist (ambiguous — is it the video or the playlist?)
https://www.youtube.com/watch?v=abc123&list=PLxyz789&index=3

# Vimeo
https://vimeo.com/123456789
https://vimeo.com/channels/staffpicks/123456789
https://vimeo.com/showcase/12345

# Direct video file
https://example.com/video.mp4
https://cdn.example.com/kids/episode1.m3u8
```

---

## Council Members

| Name | Role | Kid(s) | Lens |
|------|------|--------|------|
| **Priya** | Parent (target user) | Meera, 3 | "Will this confuse me?" |
| **Marcus** | Product Manager | Zoe & Leo, 9 | "Does this scale? What's the model?" |
| **Jake** | Dev Manager | Sam, 7 | "Can we build this? What breaks?" |
| **Anika** | UX Lead | — | "What does the parent see and feel?" |
| **Raj** | Security Expert | — | "What can go wrong with arbitrary URLs?" |

---

## Round 1: What Should We Accept?

### Priya (Parent)
> I just want to paste whatever link I find. My sister sends me a YouTube video on WhatsApp — I copy, I paste. Sometimes it's a channel my friend recommends — "just search PBSKids on YouTube" — I go there, copy the URL from the address bar. I don't know what a "playlist" is. I definitely don't know what `PL` means.
>
> Vimeo? Maybe. My daughter's preschool posts videos there sometimes. But 95% of the time it's YouTube.
>
> The error "Not a valid YouTube playlist URL" made me feel stupid. I *had* a YouTube URL. It just wasn't the right *kind*.

### Marcus (PM)
> The current model is "playlist = content source." That's too narrow. We need a **channel** concept:
>
> | URL type | Maps to | Videos come from |
> |----------|---------|------------------|
> | YT playlist | Channel (multi-video) | Playlist items via NewPipe |
> | YT channel | Channel (multi-video) | Channel uploads via NewPipe |
> | YT single video | Channel (single-video) | Just that one video |
> | Vimeo video | Channel (single-video) | Just that one video |
> | Vimeo showcase | Channel (multi-video) | Showcase items |
> | Direct URL (.mp4, .m3u8) | Channel (single-video) | Direct playback, no extraction |
>
> A "channel" is our internal model — it's "a named collection of 1+ videos that came from a URL the parent pasted." The parent never sees the word "channel" in our code sense — they see it as a row on the home screen.
>
> **Priority order:** YT video (most common paste) > YT channel > YT playlist (already done) > Vimeo video > direct URL > Vimeo showcase (rare).
>
> **Ambiguous case:** `watch?v=abc&list=PLxyz` — I say treat it as the playlist. If someone copies a link while watching a video in a playlist, they probably want the whole playlist. We can always let them add just the video separately.

### Jake (Dev)
> NewPipeExtractor already supports this. The `ServiceList.YouTube` service has:
> - `getPlaylistExtractor(url)` — we use this today
> - `getChannelExtractor(url)` — returns uploads for a channel
> - `getStreamExtractor(url)` — returns metadata for a single video
> - `getLinkTypeByUrl(url)` — **this is the key function** — it tells us if a URL is a video, playlist, channel, or unknown
>
> For Vimeo: NewPipe does NOT support Vimeo. We'd need to either:
> 1. Use Vimeo's oEmbed API (public, no key needed for public videos) to get metadata, then play via direct URL
> 2. Add a generic "direct URL" source type that just trusts the URL is playable by ExoPlayer
>
> For direct URLs (.mp4, .m3u8): ExoPlayer handles these natively. No extraction needed. We just need the URL and a parent-provided title.
>
> **DB migration:** The `playlists` table with `youtube_playlist_id` needs to become a `channels` table with `source_url`, `source_type`, and `source_id`. This is a schema migration (Room v2 → v3).
>
> **What breaks:**
> - `PlaylistUrlParser` → becomes `ContentSourceParser` (returns a typed result, not just a string)
> - `PlaylistEntity` → `ChannelEntity` with new fields
> - `PlaylistRepository.resolvePlaylist()` → needs to branch on source type
> - `PlaylistDao` → `ChannelDao`
> - Dashboard `app.js` references "playlists" everywhere — API path stays `/api/playlists` for backward compat, but response shape may change
> - Relay allowlist needs no changes (paths unchanged)

### Anika (UX)
> The parent pastes a URL. Three things can happen:
>
> 1. **Recognized + multi-video** (playlist, channel): "Added PBS Kids (42 videos)" — shows up as a row on home screen immediately with loading spinner, then thumbnails appear.
>
> 2. **Recognized + single video**: "Added 'Baby Shark Dance'" — shows up as a row with one video. This feels weird as a "playlist" — it's just one video in a row. We should show single videos differently — maybe a "My Videos" collection that groups all individually-added videos? Or just accept that a single-video row is fine for now.
>
> 3. **Unrecognized URL**: This is where current UX fails. "Not a valid YouTube playlist URL" is hostile. Better: "We couldn't find videos at this link. Try pasting a YouTube video, playlist, or channel URL."
>
> For the **ambiguous case** (`watch?v=abc&list=PLxyz`): show a toast/confirmation — "Added playlist 'Fun Videos' (12 videos)" so the parent sees they got the whole playlist, not just one video. If they wanted just the one video, they can go back and paste the video URL without `&list=`.
>
> **Dashboard input field:** Change placeholder from "YouTube playlist URL" to "Paste a YouTube link" — simpler, broader, less intimidating.
>
> **Single-video naming:** When adding a single video, auto-fetch the title (via NewPipe stream extractor) and use it as the display name. No "PLabc123" nonsense.

### Raj (Security)
> Accepting arbitrary URLs opens several attack surfaces:
>
> **1. SSRF (Server-Side Request Forgery):** The TV will fetch whatever URL is provided. If someone pastes `http://192.168.1.1/admin` or `http://169.254.169.254/latest/meta-data/` (AWS metadata), NewPipeExtractor or our HTTP client will hit that endpoint.
>
> **Mitigation:** URL allowlist by domain. Only allow:
> - `youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be`
> - `vimeo.com`, `player.vimeo.com`
> - Known CDN domains for direct video (if we support that)
> - Block private IP ranges (10.x, 172.16-31.x, 192.168.x, 127.x, 169.254.x, ::1)
>
> **2. Content injection via title:** Video titles from YouTube can contain HTML/JS. We already use `escapeHtml()` in the dashboard — make sure all new title sources go through the same sanitization.
>
> **3. Malicious direct URLs:** An `.mp4` URL could redirect to a malicious payload. ExoPlayer handles media parsing safely, but we should:
> - Follow at most 3 redirects
> - Verify Content-Type starts with `video/` or `application/x-mpegURL` before playing
> - Cap download size (ExoPlayer handles this, but be explicit)
>
> **4. Rate limiting on resolve:** Channel extraction can trigger many HTTP requests to YouTube. A parent adding 10 channels simultaneously could look like scraping. Add a queue — resolve one source at a time with a small delay between.
>
> **My recommendation:** Start with YouTube-only for v0.6. Vimeo and direct URLs can be v0.7. Each new domain we accept is a new trust boundary. YouTube via NewPipeExtractor is well-tested. Vimeo oEmbed is an unknown.

---

## Round 2: Reactions and Convergence

### Priya
> I agree with Raj — YouTube first is fine. That's 95% of what I paste. But please, PLEASE fix the error message. And let me paste a single video. That's all I really need.

### Marcus
> Agreed on YouTube-first. But I want the data model to be source-agnostic from day one. Don't name the table `youtube_channels`. Name it `channels` with a `source_type` enum. When Vimeo comes, it's just a new enum value, not a migration.
>
> Single-video grouping: Anika's "My Videos" collection is smart but adds complexity. I say a single-video row is fine for v0.6. It's a row with one thumbnail. Parents understand "I added one video, I see one video." We can group them later.
>
> **Channel limit:** Keep at 10 for now (was 10 playlists). A channel with 500 videos is heavier than a playlist with 20. We might need to cap videos-per-channel at 100 for performance.

### Jake
> Source-agnostic model — yes. Here's my proposed schema:
>
> ```sql
> CREATE TABLE channels (
>     id INTEGER PRIMARY KEY AUTOINCREMENT,
>     source_type TEXT NOT NULL,    -- 'yt_playlist', 'yt_channel', 'yt_video', 'vimeo_video', 'direct_url'
>     source_id TEXT NOT NULL,      -- YouTube ID, Vimeo ID, or full URL for direct
>     source_url TEXT NOT NULL,     -- original URL the parent pasted
>     display_name TEXT NOT NULL,
>     added_at INTEGER NOT NULL,
>     status TEXT NOT NULL DEFAULT 'active',
>     video_count INTEGER NOT NULL DEFAULT 0,
>     UNIQUE(source_type, source_id)
> );
> ```
>
> `videos` table stays mostly the same — `playlistId` becomes `channelId` referencing the new table.
>
> Migration path: rename table, add columns, backfill `source_type='yt_playlist'` and `source_url` from existing data. Room migration 2→3.
>
> For `getLinkTypeByUrl()` — NewPipe returns `StreamingService.LinkType.STREAM`, `CHANNEL`, or `PLAYLIST`. We map these directly to our `source_type`. Clean.
>
> **Videos-per-channel cap:** I'd do 200. YouTube channels can have thousands of uploads. We fetch page-by-page — just stop after 200. Show "(showing first 200 videos)" in the UI.

### Anika
> On the input field — I want to go further. Instead of just changing placeholder text, add **instant feedback** as the parent types/pastes:
>
> - Paste a URL → field border turns green + small label below: "YouTube video" or "YouTube playlist (12 videos)" or "YouTube channel"
> - Invalid URL → field border turns amber + "We don't recognize this link. Try a YouTube URL."
> - Supported but unresolvable → "Couldn't load this video. It might be private or deleted."
>
> This happens before they hit "Add." The parse is instant (regex), the resolve can show a spinner.
>
> For the **video-in-playlist ambiguous URL**: Don't show a confirmation dialog. Just add the playlist. If the parent intended the single video, the error path is: they see 12 videos instead of 1, they delete, they paste just the video URL. That's a 2-step recovery. A dialog interrupting every paste is worse.

### Raj
> On Jake's schema — `source_url` stores the original URL the parent pasted. Good for display ("where did this come from?") but **never use it for fetching after initial resolve.** Always use `source_type + source_id` to construct the canonical fetch URL. If a parent pastes a URL with tracking params or a redirect chain, we don't want to replay that on every refresh.
>
> On video-per-channel cap: Yes. 200 is fine. But paginate the fetch — don't hold 200 video metadata objects in memory during extraction. Stream them into the database in batches of 50.
>
> Domain allowlist for v0.6 (YouTube only):
> ```
> youtube.com, www.youtube.com, m.youtube.com, youtu.be, music.youtube.com
> ```
> Reject everything else with a friendly message, not a generic error.

---

## Final Spec

### Accepted URL Types (v0.6)

| Input | source_type | source_id | Example |
|-------|------------|-----------|---------|
| YT playlist URL | `yt_playlist` | `PLrAXtmErZg...` | `youtube.com/playlist?list=PLrAXtmErZg...` |
| YT video URL | `yt_video` | `dQw4w9WgXcQ` | `youtube.com/watch?v=dQw4w9WgXcQ` |
| YT short URL | `yt_video` | `dQw4w9WgXcQ` | `youtu.be/dQw4w9WgXcQ` |
| YT channel URL | `yt_channel` | `UCkMhivLMA...` | `youtube.com/@PBSKids` |
| YT channel (legacy) | `yt_channel` | `UCkMhivLMA...` | `youtube.com/channel/UCkMh...` |
| YT video+playlist | `yt_playlist` | `PLxyz789` | `youtube.com/watch?v=abc&list=PLxyz789` |

### Rejected (with friendly message)

| Input | Message |
|-------|---------|
| Vimeo URL | "Vimeo support coming soon. For now, try a YouTube link." |
| Direct .mp4/.m3u8 | "Direct video links aren't supported yet. Try a YouTube link." |
| Private/deleted video | "Couldn't load this video. It might be private or deleted." |
| Non-video URL | "We don't recognize this link. Try a YouTube video, channel, or playlist URL." |
| Private IP / localhost | "We don't recognize this link. Try a YouTube video, channel, or playlist URL." |

### Data Model

```sql
-- Migration 2 → 3
-- Rename playlists → channels, add source_type, source_url, video_count
CREATE TABLE channels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    source_url TEXT NOT NULL,
    display_name TEXT NOT NULL,
    added_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    status TEXT NOT NULL DEFAULT 'active',
    video_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE(source_type, source_id)
);

-- Backfill from playlists:
-- source_type = 'yt_playlist'
-- source_id = youtube_playlist_id
-- source_url = 'https://www.youtube.com/playlist?list=' || youtube_playlist_id
-- video_count = (SELECT COUNT(*) FROM videos WHERE playlistId = youtube_playlist_id)

-- videos table: rename playlistId → channelSourceId (matches channels.source_id)
```

### ContentSourceParser (replaces PlaylistUrlParser)

```
parse(input: String) → ContentSource?

ContentSource:
  - type: SourceType          // YT_PLAYLIST, YT_VIDEO, YT_CHANNEL
  - id: String                // YouTube ID
  - originalUrl: String       // what the parent pasted
  - canonicalUrl: String      // constructed from type+id, used for fetching
```

**Parse order:**
1. Check domain allowlist (reject non-YouTube with specific message)
2. Block private IPs
3. Use `NewPipe.getServiceByUrl(url)` + `getLinkTypeByUrl(url)` for type detection
4. Extract ID from URL via NewPipe or regex fallback
5. Return typed `ContentSource`

### Resolution by Type

| source_type | Extractor | Video cap | Title source |
|------------|-----------|-----------|--------------|
| `yt_playlist` | `getPlaylistExtractor` | 200 | Playlist name from YT |
| `yt_video` | `getStreamExtractor` | 1 | Video title from YT |
| `yt_channel` | `getChannelExtractor` | 200 | Channel name from YT |

### API Changes

- `POST /playlists` (path unchanged for backward compat)
  - Request: `{ "url": "..." }` (unchanged)
  - Response: adds `sourceType`, `videoCount` fields
  - Error messages: specific per rejection reason (see table above)
- `GET /playlists` — response includes new fields
- `DELETE /playlists/{id}` — unchanged
- Relay allowlist: no changes needed (same paths)

### Dashboard Changes

- Input placeholder: "YouTube playlist URL" → "Paste a YouTube link"
- Error messages: specific per rejection reason, always with a call-to-action ("Try a different link")
- Playlist list: show video count — "PBS Kids — 42 videos" (not just a number)
- Channel rows: show "N new since last refresh" after Refresh if new videos appeared

### Security Controls

- Domain allowlist: `youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be`, `music.youtube.com`
- Private IP block: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, 169.254.0.0/16, ::1
- Video cap: 200 per channel (stop pagination after 200)
- Resolve queue: one source at a time (no parallel YouTube scraping)
- Title sanitization: `escapeHtml()` on all titles from external sources (already in place)
- Canonical URL for fetching: never replay the original pasted URL after initial parse

### Implementation Order

1. **ContentSourceParser + tests** — parse all YT URL types, reject others with specific messages
2. **DB migration 2→3** — channels table (from playlists), add title to play_events, backfill
3. **ChannelEntity + ChannelDao** — new Room entity and DAO
4. **Resolve by type** — branch in PlaylistRepository based on source_type (playlist/channel/video)
5. **Auto-refresh on startup** — re-resolve all channels in background on app launch
6. **Video titles everywhere** — title in PlayEventEntity, in stats/recent API, in dashboard
7. **Update routes** — accept new URL types, return enriched response with error CTAs
8. **Update dashboard** — "Paste a YouTube link", video count, title in Recent Activity + Now Playing
9. **Update HomeScreen** — display name from channel, video count label

### Content Refresh Model — Auto-Update

All sources auto-refresh on app startup. No parent action required. "Refresh Videos" stays as a manual trigger but is not the primary path.

- **On app start:** Re-resolve all channels/playlists in background. New videos from channels and playlists appear automatically.
- **On add (channel):** "Added PBS Kids — 142 videos"
- **On add (single video):** "Added 'Baby Shark Dance'"
- **New video visibility:** New videos appear at the top of their channel row. No "new since last refresh" badge needed — they just show up naturally.
- **Single-video row:** Row header shows the video title (not a "channel" name). One thumbnail. No special treatment.

**Trust implication accepted:** Parents who add a channel are trusting that channel's content. This is the same trust model as letting a kid watch a YouTube channel directly — except the parent explicitly chose which channels are allowed. That's the value prop: not "we filter every video" but "you pick the sources, we remove everything else."

### TV-Side Display

| Source type | Row header | Row content |
|------------|-----------|-------------|
| Playlist | Playlist title ("Fun Videos") | Horizontal scroll of video thumbnails |
| Channel | Channel name ("PBS Kids") | Horizontal scroll of video thumbnails (up to 200) |
| Single video | Video title ("Baby Shark Dance") | Single thumbnail |

### Video Titles Everywhere (v0.4 Known Issue Fix)

Video titles must appear in **every** surface — not raw video IDs:

| Surface | Current | v0.6 |
|---------|---------|------|
| TV HomeScreen video cards | Title ✓ | Title ✓ |
| TV Now Playing overlay | Title ✓ | Title ✓ |
| Dashboard Now Playing | Video ID | **Video title** |
| Dashboard Recent Activity | Video ID | **Video title** |
| Dashboard playlist list | Playlist ID sometimes | **Display name + video count** |
| `GET /status` currentlyPlaying | Title ✓ | Title ✓ |
| `GET /stats/recent` | Video ID only | **Add title field** |

**Implementation:** `PlayEventEntity` needs a `title` field (populated at record time from `PlayEventRecorder.currentTitle`). `GET /stats/recent` response includes `title`. Dashboard `loadRecent()` renders the title.

### Out of Scope (v0.7+)

- Vimeo support
- Direct URL (.mp4, .m3u8) support
- "My Videos" grouping for single-video channels
- Per-video approve/reject within a channel
- Search within the app (parent currently searches on YouTube, copies URL)

---

## Priya's Review

*(Priya read the spec on her phone while Meera napped. Her unfiltered feedback, then the council's response.)*

### What She Said

1. **"The doc is 80% not for me."** DB schemas, migration SQL, NewPipe APIs — she doesn't need to see this. Wants a one-page "what changes for parents" summary.

2. **"Channel trust is unaddressed."** A playlist is curated by the parent. A channel is curated by a stranger on YouTube. Adding a channel means trusting that every future upload is kid-safe. The spec didn't mention this at all.

3. **"Error messages are great now."** But add "try a different link" as a call-to-action on every error.

4. **"Video count should say 'videos.'"** "PBS Kids (42)" → "PBS Kids — 42 videos."

5. **"Ambiguous URL: I don't care."** She doesn't understand the `watch?v=abc&list=PLxyz` case. "Just do whatever makes sense."

6. **"Instant feedback on paste is the killer feature."** Green border + "YouTube video — Baby Shark Dance" before tapping Add.

7. **"What does the kid see?"** The spec was all about the parent dashboard. Nothing about the TV-side experience when adding a channel vs. a single video.

### How the Council Responded

- **Marcus:** Single-video rows show video title as header. Auto-refresh on startup is the right default — parents won't tap Refresh.
- **Jake:** Auto-refresh is a background `resolveAllChannels()` call in `onCreate` — same code as manual refresh, just triggered automatically. Straightforward.
- **Anika:** All errors get CTAs. Video titles in every surface (dashboard Recent Activity has been showing raw IDs since v0.3 — finally fixing it). "Paste a YouTube link" placeholder.
- **Raj:** Auto-update is acceptable because the parent explicitly chose the channel. The trust boundary is the add decision, not each individual video. Domain allowlist still applies.

### Founder Override

> "Refresh Videos was a debug button. Auto-update everything. No parent has time to tap Refresh. And fix video titles everywhere — dashboard, analytics, all of it." — Prasanna

This overrides the council's initial snapshot mode recommendation. Channels auto-refresh on app startup. The parent's trust decision happens once, at add time.

---

## Council Vote (Final)

| Member | Vote | Key condition |
|--------|------|---------------|
| Priya | **Ship it** | Auto-update, error CTAs, "X videos" label, titles everywhere |
| Marcus | **Ship it** | Source-agnostic schema, auto-refresh on startup |
| Jake | **Ship it** | 200 video cap, resolve queue, clean migration, auto-refresh = existing code path |
| Anika | **Ship it** | "Paste a YouTube link", titles in all surfaces, error CTAs |
| Raj | **Ship it** | YouTube-only, domain allowlist, trust boundary at add time |

**Decision:** Unanimous. YouTube videos + channels + playlists in v0.6. Auto-refresh on startup. Video titles in every surface. Vimeo and direct URLs in v0.7. Source-agnostic data model from day one.
