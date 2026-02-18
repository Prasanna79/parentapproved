package tv.parentapproved.app.server

import tv.parentapproved.app.BuildConfig
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.data.events.PlayEventRecorder
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
    val title: String = "",
    val playlistTitle: String = "",
    val elapsedSec: Int = 0,
    val durationSec: Int = 0,
    val playing: Boolean = false,
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
                title = PlayEventRecorder.currentTitle ?: "",
                playlistTitle = PlayEventRecorder.currentPlaylistTitle ?: "",
                elapsedSec = (PlayEventRecorder.getElapsedMs() / 1000).toInt(),
                durationSec = (PlayEventRecorder.currentDurationMs / 1000).toInt(),
                playing = PlayEventRecorder.isPlaying,
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
