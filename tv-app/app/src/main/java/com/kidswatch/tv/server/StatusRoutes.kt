package com.kidswatch.tv.server

import com.kidswatch.tv.BuildConfig
import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.data.events.PlayEventRecorder
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val version: String,
    val serverRunning: Boolean,
    val playlistCount: Int,
    val activeSessions: Int,
    val currentlyPlaying: NowPlayingResponse?,
)

@Serializable
data class NowPlayingResponse(
    val videoId: String,
    val playlistId: String,
)

fun Route.statusRoutes() {
    get("/status") {
        val playlistCount = try {
            ServiceLocator.database.playlistDao().count()
        } catch (e: Exception) { 0 }

        val activeSessions = ServiceLocator.sessionManager.getActiveSessionCount()

        val nowPlaying = PlayEventRecorder.currentVideoId?.let { vid ->
            NowPlayingResponse(
                videoId = vid,
                playlistId = PlayEventRecorder.currentPlaylistId ?: "",
            )
        }

        call.respond(StatusResponse(
            version = BuildConfig.VERSION_NAME,
            serverRunning = true,
            playlistCount = playlistCount,
            activeSessions = activeSessions,
            currentlyPlaying = nowPlaying,
        ))
    }
}
