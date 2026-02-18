package tv.parentapproved.app.data

import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.ChannelEntity
import tv.parentapproved.app.data.cache.VideoEntity
import tv.parentapproved.app.data.models.VideoItem
import tv.parentapproved.app.util.AppLogger
import tv.parentapproved.app.util.OfflineSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

private const val MAX_VIDEOS_PER_SOURCE = 200

data class ChannelMeta(
    val id: Long,
    val sourceType: String,
    val sourceId: String,
    val sourceUrl: String,
    val displayName: String,
)

data class ResolvedSource(
    val title: String,
    val videos: List<VideoItem>,
)

object ContentSourceRepository {

    /**
     * Resolve a content source by type, returning its title and video list.
     */
    suspend fun resolve(sourceType: String, sourceId: String): ResolvedSource = withContext(Dispatchers.IO) {
        if (OfflineSimulator.isOffline) throw java.io.IOException("Simulated offline")

        when (sourceType) {
            "yt_playlist" -> resolvePlaylist(sourceId)
            "yt_video" -> resolveVideo(sourceId)
            "yt_channel" -> resolveChannel(sourceId)
            else -> throw IllegalArgumentException("Unknown source type: $sourceType")
        }
    }

    private fun resolvePlaylist(playlistId: String): ResolvedSource {
        val url = "https://www.youtube.com/playlist?list=$playlistId"
        val extractor = ServiceList.YouTube.getPlaylistExtractor(url)
        extractor.fetchPage()

        val playlistTitle = try {
            extractor.name?.takeIf { it.isNotBlank() } ?: playlistId
        } catch (_: Exception) { playlistId }

        val videos = mutableListOf<VideoItem>()

        val initialPage = extractor.initialPage
        initialPage.items.forEach { item ->
            if (videos.size >= MAX_VIDEOS_PER_SOURCE) return@forEach
            videos.add(VideoItem(
                videoId = extractVideoId(item.url),
                title = item.name ?: "(no title)",
                thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                durationSeconds = item.duration,
                playlistId = playlistId,
                position = videos.size,
            ))
        }

        var nextPage = initialPage.nextPage
        while (nextPage != null && videos.size < MAX_VIDEOS_PER_SOURCE) {
            val page = extractor.getPage(nextPage)
            page.items.forEach { item ->
                if (videos.size >= MAX_VIDEOS_PER_SOURCE) return@forEach
                videos.add(VideoItem(
                    videoId = extractVideoId(item.url),
                    title = item.name ?: "(no title)",
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                    durationSeconds = item.duration,
                    playlistId = playlistId,
                    position = videos.size,
                ))
            }
            nextPage = page.nextPage
        }

        AppLogger.success("Resolved playlist $playlistId: ${videos.size} videos, title: $playlistTitle")
        return ResolvedSource(title = playlistTitle, videos = videos)
    }

    private fun resolveVideo(videoId: String): ResolvedSource {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        val title = try {
            extractor.name?.takeIf { it.isNotBlank() } ?: videoId
        } catch (_: Exception) { videoId }

        val thumbnail = try {
            extractor.thumbnails?.firstOrNull()?.url ?: ""
        } catch (_: Exception) { "" }

        val duration = try {
            extractor.length
        } catch (_: Exception) { 0L }

        val video = VideoItem(
            videoId = videoId,
            title = title,
            thumbnailUrl = thumbnail,
            durationSeconds = duration,
            playlistId = videoId, // single video uses videoId as "playlist"
            position = 0,
        )

        AppLogger.success("Resolved video $videoId: $title")
        return ResolvedSource(title = title, videos = listOf(video))
    }

    private fun resolveChannel(channelId: String): ResolvedSource {
        val url = buildCanonicalUrl("yt_channel", channelId)
        val extractor = ServiceList.YouTube.getChannelExtractor(url)
        extractor.fetchPage()

        val channelTitle = try {
            extractor.name?.takeIf { it.isNotBlank() } ?: channelId
        } catch (_: Exception) { channelId }

        val videos = mutableListOf<VideoItem>()

        // Channel extractors use tabs — find the Videos tab
        try {
            val tabs = extractor.tabs
            val videosTab = tabs.firstOrNull { tab ->
                tab.contentFilters.any { it.contains("videos", ignoreCase = true) }
            } ?: tabs.firstOrNull() // Fall back to first tab

            if (videosTab != null) {
                val tabExtractor = ServiceList.YouTube.getChannelTabExtractor(videosTab)
                tabExtractor.fetchPage()

                val initialPage = tabExtractor.initialPage
                initialPage.items.forEach { item ->
                    if (videos.size >= MAX_VIDEOS_PER_SOURCE) return@forEach
                    videos.add(VideoItem(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: "(no title)",
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                        durationSeconds = if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) item.duration else 0L,
                        playlistId = channelId,
                        position = videos.size,
                    ))
                }

                var nextPage = initialPage.nextPage
                while (nextPage != null && videos.size < MAX_VIDEOS_PER_SOURCE) {
                    val page = tabExtractor.getPage(nextPage)
                    page.items.forEach { item ->
                        if (videos.size >= MAX_VIDEOS_PER_SOURCE) return@forEach
                        videos.add(VideoItem(
                            videoId = extractVideoId(item.url),
                            title = item.name ?: "(no title)",
                            thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                            durationSeconds = if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) item.duration else 0L,
                            playlistId = channelId,
                            position = videos.size,
                        ))
                    }
                    nextPage = page.nextPage
                }
            }
        } catch (e: Exception) {
            AppLogger.error("Channel tab extraction failed for $channelId: ${e.message}")
        }

        AppLogger.success("Resolved channel $channelId: ${videos.size} videos, title: $channelTitle")
        return ResolvedSource(title = channelTitle, videos = videos)
    }

    /**
     * Resolve all channels sequentially (not parallel — avoids rate limiting).
     */
    suspend fun resolveAllChannels(
        channels: List<ChannelMeta>,
        db: CacheDatabase,
    ): Map<String, SourceResult> {
        val results = mutableMapOf<String, SourceResult>()
        for (meta in channels) {
            results[meta.sourceId] = try {
                val resolved = resolve(meta.sourceType, meta.sourceId)
                cacheVideos(db, meta.sourceId, resolved.videos)

                // Update display name and video count
                val entity = db.channelDao().getBySourceId(meta.sourceId)
                if (entity != null) {
                    val newName = if (resolved.title.isNotBlank() && resolved.title != meta.sourceId) resolved.title else entity.displayName
                    db.channelDao().updateMeta(entity.id, newName, resolved.videos.size)
                }

                SourceResult.Success(resolved.videos)
            } catch (e: Exception) {
                AppLogger.error("Resolve ${meta.sourceId} failed: ${e.message}")
                val cached = getCachedVideos(db, meta.sourceId)
                if (cached.isNotEmpty()) {
                    SourceResult.CachedFallback(cached)
                } else {
                    SourceResult.Error(e.message ?: "Unknown error")
                }
            }
        }
        return results
    }

    suspend fun cacheVideos(db: CacheDatabase, sourceId: String, videos: List<VideoItem>) {
        withContext(Dispatchers.IO) {
            db.videoDao().deleteByPlaylist(sourceId)
            db.videoDao().insertAll(videos.map { v ->
                VideoEntity(
                    videoId = v.videoId,
                    playlistId = v.playlistId,
                    title = v.title,
                    thumbnailUrl = v.thumbnailUrl,
                    durationSeconds = v.durationSeconds,
                    position = v.position,
                )
            })
        }
    }

    suspend fun getCachedVideos(db: CacheDatabase, sourceId: String): List<VideoItem> {
        return withContext(Dispatchers.IO) {
            db.videoDao().getByPlaylist(sourceId).map { e ->
                VideoItem(
                    videoId = e.videoId,
                    title = e.title,
                    thumbnailUrl = e.thumbnailUrl,
                    durationSeconds = e.durationSeconds,
                    playlistId = e.playlistId,
                    position = e.position,
                )
            }
        }
    }

    fun buildCanonicalUrl(sourceType: String, sourceId: String): String {
        return when (sourceType) {
            "yt_playlist" -> "https://www.youtube.com/playlist?list=$sourceId"
            "yt_video" -> "https://www.youtube.com/watch?v=$sourceId"
            "yt_channel" -> {
                when {
                    sourceId.startsWith("UC") -> "https://www.youtube.com/channel/$sourceId"
                    sourceId.startsWith("@") -> "https://www.youtube.com/$sourceId"
                    sourceId.startsWith("c/") || sourceId.startsWith("user/") -> "https://www.youtube.com/$sourceId"
                    else -> "https://www.youtube.com/$sourceId"
                }
            }
            else -> sourceId
        }
    }

    private fun extractVideoId(url: String): String {
        val match = Regex("[?&]v=([a-zA-Z0-9_-]+)").find(url)
        return match?.groupValues?.get(1) ?: url.substringAfterLast("/")
    }
}

sealed class SourceResult {
    data class Success(val videos: List<VideoItem>) : SourceResult()
    data class CachedFallback(val videos: List<VideoItem>) : SourceResult()
    data class Error(val message: String) : SourceResult()
}
