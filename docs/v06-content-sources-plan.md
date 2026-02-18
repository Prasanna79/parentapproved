# ParentApproved v0.6 "Content Sources" — Implementation Plan

## Context

v0.4 only accepts YouTube playlist URLs (`?list=PL...`). Parents paste video links, channel links, show links, and get "Not a valid YouTube playlist URL." v0.6 accepts any YouTube URL — videos, channels, playlists, shows — with a source-agnostic data model ready for Vimeo in v0.7. Also fixes video titles showing as raw IDs in the dashboard (known issue since v0.3).

Full spec: `docs/napkin-v6-content-sources-council.md`

---

## Step 0: Test URL Fixture + TDD Harness

**Create** `tv-app/app/src/test/java/tv/parentapproved/app/util/UrlFixtures.kt`
**Create** `tv-app/app/src/test/java/tv/parentapproved/app/util/ContentSourceParserTest.kt`

Write ALL tests first against the fixture list. Tests call `ContentSourceParser.parse()` which doesn't exist yet — every test fails. Then implement the parser in Step 1 to make them pass.

### URL Fixture Table

The fixture file is a single list of `TestUrl` data classes:

```kotlin
data class TestUrl(
    val input: String,
    val expectedType: String?,       // "yt_playlist", "yt_video", "yt_channel", or null for rejection
    val expectedId: String?,         // extracted ID, or null for rejection
    val expectedRejectMsg: String?,  // substring of rejection message, or null for success
    val tag: String,                 // human label for the test
)
```

### The URL Zoo (73 test URLs)

#### YouTube Playlists — should parse as YT_PLAYLIST (16 URLs)

| # | URL | Expected ID | Tag |
|---|-----|-------------|-----|
| 1 | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | bare playlist ID |
| 2 | `https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | standard playlist URL |
| 3 | `https://m.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | mobile playlist URL |
| 4 | `https://music.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | music.youtube playlist |
| 5 | `https://www.youtube.com/playlist?list=PLtest123&index=5` | `PLtest123` | playlist with extra params |
| 6 | `https://www.youtube.com/watch?v=abc123&list=PLxyz789` | `PLxyz789` | video+playlist ambiguous (playlist wins) |
| 7 | `https://www.youtube.com/watch?v=abc123&list=PLxyz789&index=3` | `PLxyz789` | video+playlist+index |
| 8 | `https://m.youtube.com/watch?v=abc123&list=PLxyz789` | `PLxyz789` | mobile video+playlist |
| 9 | `  PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf  ` | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | bare ID with whitespace |
| 10 | `https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf\n` | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | URL with trailing newline |
| 11 | `https://www.youtube.com/show/VLPLa8HWWMcQEGS8pJ-4UKas815MNxTNKoy8?sbp=KgszSU1MTFpCcDNod0AB` | `PLa8HWWMcQEGS8pJ-4UKas815MNxTNKoy8` | show URL (VL prefix stripped) |
| 12 | `https://www.youtube.com/show/VLPLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | `PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf` | show URL without query params |
| 13 | `https://m.youtube.com/show/VLPLtest123?sbp=abc` | `PLtest123` | mobile show URL |
| 14 | `https://www.youtube.com/watch?list=PLxyz789&v=abc123` | `PLxyz789` | list param before v param |
| 15 | `PLshort` | `PLshort` | minimal bare playlist ID |
| 16 | `https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI` | `PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI` | playlist ID with underscores |

#### YouTube Videos — should parse as YT_VIDEO (12 URLs)

| # | URL | Expected ID | Tag |
|---|-----|-------------|-----|
| 17 | `https://www.youtube.com/watch?v=dQw4w9WgXcQ` | `dQw4w9WgXcQ` | standard video URL |
| 18 | `https://youtu.be/dQw4w9WgXcQ` | `dQw4w9WgXcQ` | youtu.be short URL |
| 19 | `https://m.youtube.com/watch?v=dQw4w9WgXcQ` | `dQw4w9WgXcQ` | mobile video URL |
| 20 | `https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120` | `dQw4w9WgXcQ` | video with timestamp |
| 21 | `https://youtu.be/dQw4w9WgXcQ?t=120` | `dQw4w9WgXcQ` | short URL with timestamp |
| 22 | `https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share` | `dQw4w9WgXcQ` | video with feature param |
| 23 | `https://music.youtube.com/watch?v=dQw4w9WgXcQ` | `dQw4w9WgXcQ` | music.youtube video |
| 24 | `https://www.youtube.com/shorts/dQw4w9WgXcQ` | `dQw4w9WgXcQ` | YouTube Shorts URL |
| 25 | `https://www.youtube.com/embed/dQw4w9WgXcQ` | `dQw4w9WgXcQ` | embed URL |
| 26 | `https://www.youtube.com/v/dQw4w9WgXcQ` | `dQw4w9WgXcQ` | old embed URL |
| 27 | `https://youtu.be/dQw4w9WgXcQ?si=sharetoken123` | `dQw4w9WgXcQ` | short URL with share token |
| 28 | `https://www.youtube.com/live/dQw4w9WgXcQ` | `dQw4w9WgXcQ` | live stream URL |

#### YouTube Channels — should parse as YT_CHANNEL (10 URLs)

| # | URL | Expected ID | Tag |
|---|-----|-------------|-----|
| 29 | `https://www.youtube.com/@PBSKids` | `@PBSKids` | @handle URL |
| 30 | `https://www.youtube.com/channel/UCkMhivLMAaI-cpNwFPaOYUQ` | `UCkMhivLMAaI-cpNwFPaOYUQ` | channel/UC... URL |
| 31 | `https://www.youtube.com/c/PBSKids` | `c/PBSKids` | /c/name URL |
| 32 | `https://www.youtube.com/user/PBSKids` | `user/PBSKids` | /user/name URL |
| 33 | `https://m.youtube.com/@PBSKids` | `@PBSKids` | mobile @handle |
| 34 | `https://www.youtube.com/@PBSKids/videos` | `@PBSKids` | @handle with /videos suffix |
| 35 | `https://www.youtube.com/@PBSKids/playlists` | `@PBSKids` | @handle with /playlists suffix |
| 36 | `https://www.youtube.com/channel/UCkMhivLMAaI-cpNwFPaOYUQ/videos` | `UCkMhivLMAaI-cpNwFPaOYUQ` | channel URL with /videos |
| 37 | `https://www.youtube.com/@PBS-Kids` | `@PBS-Kids` | @handle with hyphen |
| 38 | `https://www.youtube.com/@PBSKids/featured` | `@PBSKids` | @handle with /featured |

#### Rejections — Vimeo (3 URLs)

| # | URL | Expected message substring | Tag |
|---|-----|---------------------------|-----|
| 39 | `https://vimeo.com/123456789` | `Vimeo` | Vimeo video |
| 40 | `https://vimeo.com/channels/staffpicks/123456789` | `Vimeo` | Vimeo channel |
| 41 | `https://vimeo.com/showcase/12345` | `Vimeo` | Vimeo showcase |

#### Rejections — Direct file URLs (3 URLs)

| # | URL | Expected message substring | Tag |
|---|-----|---------------------------|-----|
| 42 | `https://example.com/video.mp4` | `not supported yet` | .mp4 direct link |
| 43 | `https://cdn.example.com/kids/episode1.m3u8` | `not supported yet` | .m3u8 direct link |
| 44 | `https://example.com/video.webm` | `not supported yet` | .webm direct link |

#### Rejections — Private/internal IPs (7 URLs)

| # | URL | Expected message substring | Tag |
|---|-----|---------------------------|-----|
| 45 | `http://192.168.1.1/admin` | `don't recognize` | 192.168.x private |
| 46 | `http://10.0.0.1/api` | `don't recognize` | 10.x private |
| 47 | `http://172.16.0.1/page` | `don't recognize` | 172.16.x private |
| 48 | `http://127.0.0.1:8080/` | `don't recognize` | localhost |
| 49 | `http://169.254.169.254/latest/meta-data/` | `don't recognize` | AWS metadata (link-local) |
| 50 | `http://localhost:3000/` | `don't recognize` | localhost hostname |
| 51 | `http://[::1]/` | `don't recognize` | IPv6 loopback |

#### Rejections — Non-video URLs (8 URLs)

| # | URL | Expected message substring | Tag |
|---|-----|---------------------------|-----|
| 52 | `https://www.google.com` | `don't recognize` | random website |
| 53 | `https://twitter.com/video/123` | `don't recognize` | Twitter/X |
| 54 | `not a url at all` | `don't recognize` | plain text gibberish |
| 55 | `` (empty string) | `don't recognize` | empty string |
| 56 | `   ` | `don't recognize` | whitespace only |
| 57 | `https://` | `don't recognize` | incomplete URL |
| 58 | `https://www.youtube.com/` | `don't recognize` | YouTube homepage (no content) |
| 59 | `https://www.youtube.com/feed/trending` | `don't recognize` | YouTube trending page |

#### Rejections — YouTube show URL edge cases (2 URLs)

| # | URL | Expected message substring | Tag |
|---|-----|---------------------------|-----|
| 60 | `https://www.youtube.com/show/NOTAPLAYLIST` | `don't recognize` | show URL without VL+PL prefix |
| 61 | `https://www.youtube.com/show/` | `don't recognize` | show URL with empty ID |

#### Edge cases — tricky URLs (12 URLs)

| # | URL | Expected type | Expected ID | Tag |
|---|-----|--------------|-------------|-----|
| 62 | `youtube.com/watch?v=abc123` | `yt_video` | `abc123` | no protocol, no www |
| 63 | `HTTP://WWW.YOUTUBE.COM/WATCH?V=abc123` | `yt_video` | `abc123` | uppercase URL |
| 64 | `https://www.youtube.com/watch?v=abc-_123` | `yt_video` | `abc-_123` | ID with dash and underscore |
| 65 | `https://www.youtube.com/watch?app=desktop&v=abc123` | `yt_video` | `abc123` | v param not first |
| 66 | `https://www.youtube.com/watch?v=abc123#t=30` | `yt_video` | `abc123` | fragment in URL |
| 67 | `https://youtu.be/` | reject | — | short URL with empty ID |
| 68 | `https://www.youtube.com/watch?v=` | reject | — | empty v param |
| 69 | `https://www.youtube.com/watch` | reject | — | watch with no params |
| 70 | `https://www.youtube.com/channel/` | reject | — | empty channel ID |
| 71 | `https://www.youtube.com/@` | reject | — | empty handle |
| 72 | `https://www.youtube.com/playlist?list=RD` | reject | — | YouTube Mix (RD prefix, not a real playlist) |
| 73 | `https://www.youtube.com/playlist?list=LL` | reject | — | Liked Videos (LL, requires auth) |

### Test Structure

Tests are organized as parameterized groups using the fixture list:

```kotlin
@Test fun `playlist URLs parse correctly`()     // fixtures 1-16
@Test fun `video URLs parse correctly`()         // fixtures 17-28
@Test fun `channel URLs parse correctly`()       // fixtures 29-38
@Test fun `vimeo URLs rejected with message`()   // fixtures 39-41
@Test fun `direct file URLs rejected`()          // fixtures 42-44
@Test fun `private IPs blocked`()                // fixtures 45-51
@Test fun `non-video URLs rejected`()            // fixtures 52-59
@Test fun `show URL edge cases rejected`()       // fixtures 60-61
@Test fun `tricky edge cases handled`()          // fixtures 62-73
```

Each test iterates the fixture subset and asserts the parse result. On failure, the assertion message includes the tag so you know exactly which URL broke.

### TDD Workflow

1. Write `UrlFixtures.kt` with all 73 entries
2. Write `ContentSourceParserTest.kt` with 9 test methods that iterate fixtures
3. Create a stub `ContentSourceParser.kt` that returns `Rejected("not implemented")` for everything
4. Run tests → all 73 fail (9 test methods fail)
5. Implement parser rule by rule, running tests after each rule:
   - Bare playlist ID → ~2 tests go green
   - Domain allowlist → rejections start going green
   - Private IP block → more rejections green
   - `?list=` param → playlist URLs green
   - `/show/VL` → show URLs green
   - `youtu.be/` → short URLs green
   - `?v=` → video URLs green
   - `/channel/`, `/@`, `/c/`, `/user/` → channel URLs green
   - Edge cases last
6. All 73 pass → parser is complete

---

## Step 1: ContentSourceParser Implementation

**Create** `tv-app/app/src/main/java/tv/parentapproved/app/util/ContentSourceParser.kt`

Replaces `PlaylistUrlParser`. Pure regex parsing (no NewPipe at parse time).

```kotlin
sealed class ParseResult {
    data class Success(val source: ContentSource) : ParseResult()
    data class Rejected(val message: String) : ParseResult()
}

data class ContentSource(
    val type: SourceType,
    val id: String,
    val originalUrl: String,
    val canonicalUrl: String,
)

enum class SourceType { YT_PLAYLIST, YT_VIDEO, YT_CHANNEL }
```

**Parse order:**
1. Trim + strip whitespace/newlines
2. Empty/blank → reject
3. Bare playlist ID (`PL[a-zA-Z0-9_-]+`) → `YT_PLAYLIST`
4. Not a URL (no `.` or `://`) → reject
5. Normalize: lowercase protocol + domain, add `https://` if missing
6. Private IP check (10.x, 172.16-31.x, 192.168.x, 127.x, 169.254.x, localhost, ::1) → reject
7. Direct file URL (.mp4, .m3u8, .webm, .avi, .mkv, .mov) → reject with specific message
8. Vimeo domain → reject with "Vimeo support coming soon"
9. Domain allowlist (youtube.com, www.youtube.com, m.youtube.com, youtu.be, music.youtube.com) — reject others
10. URL with `?list=PL...` → `YT_PLAYLIST` (ambiguous video+playlist → playlist wins). Reject `?list=RD` (Mix) and `?list=LL` (Liked Videos).
11. `/show/VL(PL[a-zA-Z0-9_-]+)` → strip `VL` → `YT_PLAYLIST`. Reject `/show/` without VLPL.
12. `youtu.be/{ID}` where ID is non-empty `[a-zA-Z0-9_-]+` → `YT_VIDEO`
13. `/shorts/{ID}`, `/embed/{ID}`, `/v/{ID}`, `/live/{ID}` → `YT_VIDEO`
14. `?v={ID}` where ID is non-empty → `YT_VIDEO`
15. `/channel/(UC[a-zA-Z0-9_-]+)` → `YT_CHANNEL` (strip `/videos`, `/playlists`, `/featured` suffix)
16. `/@{handle}` where handle is non-empty → `YT_CHANNEL` (strip subpath)
17. `/c/{name}`, `/user/{name}` → `YT_CHANNEL`
18. Anything else on YouTube domain (homepage, /feed/*, etc.) → reject

**Canonical URL builder:**

| Type | Canonical URL |
|------|--------------|
| `YT_PLAYLIST` | `https://www.youtube.com/playlist?list={id}` |
| `YT_VIDEO` | `https://www.youtube.com/watch?v={id}` |
| `YT_CHANNEL` | `https://www.youtube.com/{id}` (for `@handle`, `c/name`, `user/name`) or `https://www.youtube.com/channel/{id}` (for UC IDs) |

---

## Step 2: DB Migration 2→3 + New Entities

**Create** `tv-app/app/src/main/java/tv/parentapproved/app/data/cache/ChannelEntity.kt`
**Create** `tv-app/app/src/main/java/tv/parentapproved/app/data/cache/ChannelDao.kt`
**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/data/cache/CacheDatabase.kt`
**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/data/events/PlayEventEntity.kt`
**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/data/events/PlayEventRecorder.kt`

### ChannelEntity schema
```sql
CREATE TABLE channels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_type TEXT NOT NULL,    -- 'yt_playlist', 'yt_video', 'yt_channel'
    source_id TEXT NOT NULL,
    source_url TEXT NOT NULL,     -- original URL parent pasted
    display_name TEXT NOT NULL,
    added_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    video_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE(source_type, source_id)
);
```

### ChannelDao methods
```kotlin
insert(channel: ChannelEntity): Long
getAll(): List<ChannelEntity>
getBySourceKey(sourceType: String, sourceId: String): ChannelEntity?
getBySourceId(sourceId: String): ChannelEntity?
deleteById(id: Long)
count(): Int
deleteAll()
updateDisplayName(id: Long, name: String)
updateVideoCount(id: Long, count: Int)
```

### Migration 2→3 SQL
1. Create `channels` table
2. `INSERT INTO channels (id, source_type, source_id, source_url, display_name, added_at, status, video_count) SELECT id, 'yt_playlist', youtube_playlist_id, 'https://www.youtube.com/playlist?list=' || youtube_playlist_id, display_name, added_at, status, 0 FROM playlists`
3. Update video_count: `UPDATE channels SET video_count = (SELECT COUNT(*) FROM videos WHERE playlistId = channels.source_id)`
4. `DROP TABLE playlists`
5. `ALTER TABLE play_events ADD COLUMN title TEXT NOT NULL DEFAULT ''`
6. Backfill titles: `UPDATE play_events SET title = COALESCE((SELECT v.title FROM videos v WHERE v.videoId = play_events.videoId LIMIT 1), '')`

### CacheDatabase changes
- Version 2 → 3
- Entities: replace `PlaylistEntity` with `ChannelEntity`
- Add `abstract fun channelDao(): ChannelDao`
- Remove `abstract fun playlistDao(): PlaylistDao`
- Add `MIGRATION_2_3`

### PlayEventEntity change
Add `val title: String = ""` field.

### PlayEventRecorder change
In `startEvent()`, persist `title` into the entity:
```kotlin
val event = PlayEventEntity(
    videoId = videoId,
    playlistId = playlistId,
    startedAt = currentStartTime,
    title = title,  // NEW
)
```

**~10 tests** for ChannelDao CRUD (in androidTest since Room needs context, or use in-memory DB in unit tests).

---

## Step 3: ContentSourceRepository (evolve PlaylistRepository)

**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/data/PlaylistRepository.kt`

Rename to `ContentSourceRepository` (or keep filename, rename object). Dispatch resolve by source type:

| source_type | NewPipe extractor | Video cap | Notes |
|------------|-------------------|-----------|-------|
| `yt_playlist` | `getPlaylistExtractor(url)` | 200 | Existing code + cap |
| `yt_video` | `getStreamExtractor(url)` | 1 | New: returns 1-video list |
| `yt_channel` | `getChannelExtractor(url)` | 200 | New: paginate uploads |

### New data classes
```kotlin
data class ChannelMeta(
    val id: Long,
    val sourceType: String,
    val sourceId: String,
    val displayName: String,
    val canonicalUrl: String,
)

data class ResolvedSource(
    val title: String,
    val videos: List<VideoItem>,
)
```

### Key methods
- `resolve(sourceType: String, sourceId: String, canonicalUrl: String): ResolvedSource` — single dispatch point
- `resolveAllChannels(channels: List<ChannelMeta>, db: CacheDatabase): Map<String, PlaylistResult>` — **sequential** (one at a time, not parallel) per security spec
- `buildCanonicalUrl(type: SourceType, id: String): String`

### Resolve logic
- `yt_playlist`: existing `resolvePlaylist()` code, add 200-video cap
- `yt_video`: call `getStreamExtractor(canonicalUrl)`, build single `VideoItem` from metadata
- `yt_channel`: call `getChannelExtractor(canonicalUrl)`, paginate uploads up to 200

### Sequential resolve
Replace `async/awaitAll` pattern with sequential loop:
```kotlin
for (meta in channels) {
    val result = try { ... } catch { ... }
    results[meta.sourceId] = result
}
```

Keep old method names as aliases during transition, remove in Step 9.

**~8 tests** for dispatch, canonical URL building, video cap.

---

## Step 4: Update Server Routes

**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/server/PlaylistRoutes.kt`
**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/server/StatsRoutes.kt`
**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/server/StatusRoutes.kt`

### POST /playlists
- Use `ContentSourceParser.parse()` instead of `PlaylistUrlParser.parse()`
- On `ParseResult.Rejected` → 400 with `{ "error": "...", "cta": "Try a different link" }`
- Insert into `channelDao()` with source_type, source_id, source_url
- Duplicate check: `channelDao().getBySourceKey(type, id)`

### PlaylistResponse changes
```kotlin
data class PlaylistResponse(
    val id: Long,
    val youtubePlaylistId: String,  // kept for backward compat (= sourceId)
    val sourceType: String,         // NEW
    val sourceId: String,           // NEW
    val sourceUrl: String,          // NEW
    val displayName: String,
    val addedAt: Long,
    val status: String,
    val videoCount: Int,            // NEW
)
```

### GET /playlists
- Read from `channelDao().getAll()`
- Map `ChannelEntity` → `PlaylistResponse` with new fields

### DELETE /playlists/{id}
- Switch from `playlistDao()` to `channelDao()`
- Delete cached videos by `entity.sourceId` instead of `youtubePlaylistId`

### StatsRoutes — GET /stats/recent
- Add `title` field to `RecentEventResponse`
- Map from `PlayEventEntity.title`

### StatusRoutes
- `channelDao().count()` instead of `playlistDao().count()`

**~10 tests** (modify existing + new for video/channel URL acceptance and error CTAs).

---

## Step 5: Update HomeViewModel + HomeScreen

**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/ui/screens/HomeViewModel.kt`
**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/ui/screens/HomeScreen.kt`
**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/ui/screens/PlaybackScreen.kt`

### HomeViewModel
- `PlaylistRow` → `ChannelRow` (add `sourceType: String`, `videoCount: Int`, `sourceId: String`)
- Read from `channelDao().getAll()`
- Build `ChannelMeta` list for `resolveAllChannels()`
- Update result mapping to use `sourceId` instead of `youtubePlaylistId`

### HomeScreen
- Empty state text: "No videos yet!" / "Connect your phone to add YouTube videos"
- Loading text: "Loading videos..."
- Row header: `displayName` + video count
- Section label: "PBS Kids — 42 videos" format
- `PlaylistRowSection` → `ChannelRowSection` (rename)

### PlaybackScreen
- Line 213: `db.playlistDao().getByYoutubeId(playlistId)` → `db.channelDao().getBySourceId(playlistId)`
- Update `entity?.displayName` reference

---

## Step 6: Auto-Refresh on Startup

**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/ParentApprovedApp.kt`

Add background coroutine in `onCreate()` after `ServiceLocator.init()`:
```kotlin
CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
    try {
        val channels = ServiceLocator.database.channelDao().getAll()
        if (channels.isNotEmpty()) {
            val metas = channels.map { ch ->
                ChannelMeta(
                    id = ch.id,
                    sourceType = ch.sourceType,
                    sourceId = ch.sourceId,
                    displayName = ch.displayName,
                    canonicalUrl = ContentSourceParser.buildCanonicalUrl(
                        SourceType.valueOf(ch.sourceType.uppercase()),
                        ch.sourceId,
                    ),
                )
            }
            ContentSourceRepository.resolveAllChannels(metas, ServiceLocator.database)
        }
    } catch (e: Exception) {
        AppLogger.error("Auto-refresh failed: ${e.message}")
    }
}
```

Sequential resolve. HomeViewModel shows cached data immediately, fresh data when resolve completes.

---

## Step 7: Dashboard Updates

**Modify** (both local + relay copies):
- `tv-app/app/src/main/assets/index.html` + `relay/assets/index.html`
- `tv-app/app/src/main/assets/app.js` + `relay/assets/app.js`

### HTML changes
- Input placeholder: `"YouTube playlist URL"` → `"Paste a YouTube link"`
- Button text: `"Add Playlist"` → `"Add"` (if present)

### app.js changes

#### `loadPlaylists()`
Show display name + video count:
```javascript
li.innerHTML = '<span>' + escapeHtml(pl.displayName) +
    (pl.videoCount ? ' — ' + pl.videoCount + ' videos' : '') +
    '</span>';
```

#### `loadRecent()`
Show title instead of videoId:
```javascript
li.innerHTML = '<span>' + escapeHtml(evt.title || evt.videoId) +
    '</span><span>' + mins + 'm</span>';
```

#### Error display
Show `data.cta` if present in error responses:
```javascript
playlistError.textContent = result.data.error +
    (result.data.cta ? ' ' + result.data.cta : '');
```

#### `deletePlaylist()` confirm text
`'Remove this playlist?'` → `'Remove this source?'`

---

## Step 8: Update DebugReceiver

**Modify** `tv-app/app/src/main/java/tv/parentapproved/app/debug/DebugReceiver.kt`

All playlist-related handlers switch to `channelDao()` and `ContentSourceParser`:

- `handleAddPlaylist` → use `ContentSourceParser.parse()`, insert into `channelDao()`
- `handleRemovePlaylist` → `channelDao().deleteById()`
- `handleResolvePlaylist` → dispatch via `ContentSourceRepository.resolve()`
- `handleRefreshPlaylists` → `channelDao().getAll()`
- `handleGetPlaylists` → read from `channelDao()`, include `sourceType`/`sourceId`
- `handleGetServerStatus` → `channelDao().count()`
- `handleFullReset` → `channelDao().deleteAll()`
- `handleGetStateDump` → `channelDao().count()`

---

## Step 9: Cleanup

**Delete:**
- `tv-app/app/src/main/java/tv/parentapproved/app/data/cache/PlaylistEntity.kt`
- `tv-app/app/src/main/java/tv/parentapproved/app/data/cache/PlaylistDao.kt`
- `tv-app/app/src/main/java/tv/parentapproved/app/util/PlaylistUrlParser.kt`
- `tv-app/app/src/test/java/tv/parentapproved/app/util/PlaylistUrlParserTest.kt`

Remove from `CacheDatabase`: `PlaylistEntity` import, `playlistDao()` method.

Remove old `PlaylistMeta`, `PlaylistRepository` object name references (already renamed in Step 3).

Grep for any remaining `playlistDao()` or `PlaylistUrlParser` references — should be zero.

---

## Dependency Graph

```
Step 0 (Test fixtures) ──> Step 1 (Parser implementation)
                                    │
Step 2 (DB/Entities) ──────────────┤
                                    ▼
                           Step 3 (Repository)
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
             Step 4 (Routes)  Step 5 (UI)    Step 7 (Dashboard)
                    │               │
                    │               ▼
                    │        Step 6 (Auto-refresh)
                    │
                    ▼
             Step 8 (Debug)
                    │
                    ▼
        All ──> Step 9 (Cleanup)
```

Steps 0+1 and 2 are independent (parallel). Steps 4, 5, 7, 8 are independent after Step 3 (parallel).

---

## Verification

1. `./gradlew testDebugUnitTest` — all tests pass (~240+ including ~55 new)
2. `cd relay && npx vitest run` — 139 relay tests still pass (no relay code changes except dashboard JS)
3. **Manual test on emulator:**
   - Paste YouTube video URL → added as 1-video row
   - Paste YouTube channel URL → added as multi-video row with title + count
   - Paste YouTube playlist URL → works as before
   - Paste YouTube show URL (`/show/VLPL...`) → added as playlist
   - Paste Vimeo URL → "Vimeo support coming soon. Try a YouTube link."
   - Paste gibberish → friendly error + CTA
   - Dashboard Recent Activity shows video titles, not IDs
   - Dashboard source list shows "PBS Kids — 42 videos"
   - App restart → channels auto-refresh in background
4. **Install on Mi Box** and verify real hardware
5. Total new tests: ~55

---

## Files Summary (28 files touched)

| Action | Count | Files |
|--------|-------|-------|
| CREATE | 5 | ContentSourceParser.kt, ContentSourceParserTest.kt, UrlFixtures.kt, ChannelEntity.kt, ChannelDao.kt |
| MODIFY | 19 | CacheDatabase, PlayEventEntity, PlayEventRecorder, PlaylistRepository→ContentSourceRepository, PlaylistRoutes, StatsRoutes, StatusRoutes, HomeViewModel, HomeScreen, PlaybackScreen, ParentApprovedApp, DebugReceiver, 2x index.html, 2x app.js, 3x test files |
| DELETE | 4 | PlaylistEntity, PlaylistDao, PlaylistUrlParser, PlaylistUrlParserTest |
