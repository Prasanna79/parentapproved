package com.kidswatch.tv.data

import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.cache.VideoEntity
import com.kidswatch.tv.data.models.VideoItem
import com.kidswatch.tv.util.AppLogger
import com.kidswatch.tv.util.OfflineSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

data class PlaylistMeta(
    val id: Long,
    val youtubePlaylistId: String,
    val displayName: String,
)

object PlaylistRepository {

    suspend fun resolvePlaylist(playlistId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        if (OfflineSimulator.isOffline) throw java.io.IOException("Simulated offline")

        val url = "https://www.youtube.com/playlist?list=$playlistId"
        val extractor = ServiceList.YouTube.getPlaylistExtractor(url)
        extractor.fetchPage()

        val videos = mutableListOf<VideoItem>()

        // First page
        val initialPage = extractor.initialPage
        initialPage.items.forEach { item ->
            videos.add(
                VideoItem(
                    videoId = extractVideoId(item.url),
                    title = item.name ?: "(no title)",
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                    durationSeconds = item.duration,
                    playlistId = playlistId,
                    position = videos.size,
                )
            )
        }

        // Subsequent pages
        var nextPage = initialPage.nextPage
        while (nextPage != null) {
            val page = extractor.getPage(nextPage)
            page.items.forEach { item ->
                videos.add(
                    VideoItem(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: "(no title)",
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                        durationSeconds = item.duration,
                        playlistId = playlistId,
                        position = videos.size,
                    )
                )
            }
            nextPage = page.nextPage
        }

        AppLogger.success("Resolved $playlistId: ${videos.size} videos")
        videos
    }

    suspend fun resolveAllPlaylists(
        playlists: List<PlaylistMeta>,
        db: CacheDatabase,
    ): Map<String, PlaylistResult> = coroutineScope {
        playlists.map { meta ->
            async {
                meta.youtubePlaylistId to try {
                    val videos = resolvePlaylist(meta.youtubePlaylistId)
                    cacheVideos(db, meta.youtubePlaylistId, videos)
                    PlaylistResult.Success(videos)
                } catch (e: Exception) {
                    AppLogger.error("Resolve ${meta.youtubePlaylistId} failed: ${e.message}")
                    val cached = getCachedVideos(db, meta.youtubePlaylistId)
                    if (cached.isNotEmpty()) {
                        PlaylistResult.CachedFallback(cached)
                    } else {
                        PlaylistResult.Error(e.message ?: "Unknown error")
                    }
                }
            }
        }.awaitAll().toMap()
    }

    suspend fun cacheVideos(db: CacheDatabase, playlistId: String, videos: List<VideoItem>) {
        withContext(Dispatchers.IO) {
            db.videoDao().deleteByPlaylist(playlistId)
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

    suspend fun getCachedVideos(db: CacheDatabase, playlistId: String): List<VideoItem> {
        return withContext(Dispatchers.IO) {
            db.videoDao().getByPlaylist(playlistId).map { e ->
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

    private fun extractVideoId(url: String): String {
        val match = Regex("[?&]v=([a-zA-Z0-9_-]+)").find(url)
        return match?.groupValues?.get(1) ?: url.substringAfterLast("/")
    }
}

sealed class PlaylistResult {
    data class Success(val videos: List<VideoItem>) : PlaylistResult()
    data class CachedFallback(val videos: List<VideoItem>) : PlaylistResult()
    data class Error(val message: String) : PlaylistResult()
}
