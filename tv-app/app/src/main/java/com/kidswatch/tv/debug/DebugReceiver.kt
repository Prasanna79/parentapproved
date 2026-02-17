package com.kidswatch.tv.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.auth.PinResult
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.cache.PlaylistEntity
import com.kidswatch.tv.data.events.PlayEventRecorder
import com.kidswatch.tv.util.AppLogger
import com.kidswatch.tv.util.NetworkUtils
import com.kidswatch.tv.util.OfflineSimulator
import com.kidswatch.tv.util.PlaylistUrlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DebugReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "KidsWatch-Intent"
        private const val PKG = "com.kidswatch.tv"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val json = Json { prettyPrint = false }

        // Callback for play/stop actions (set by MainActivity)
        var onPlayVideo: ((videoId: String, playlistId: String) -> Unit)? = null
        var onStopPlayback: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // --- Server/Playlists ---
            "$PKG.DEBUG_ADD_PLAYLIST" -> handleAddPlaylist(context, intent)
            "$PKG.DEBUG_REMOVE_PLAYLIST" -> handleRemovePlaylist(context, intent)
            "$PKG.DEBUG_RESOLVE_PLAYLIST" -> handleResolvePlaylist(intent)
            "$PKG.DEBUG_REFRESH_PLAYLISTS" -> handleRefreshPlaylists()
            "$PKG.DEBUG_GET_PLAYLISTS" -> handleGetPlaylists()
            "$PKG.DEBUG_GET_SERVER_STATUS" -> handleGetServerStatus(context)

            // --- PIN/Auth ---
            "$PKG.DEBUG_GET_PIN" -> handleGetPin()
            "$PKG.DEBUG_RESET_PIN" -> handleResetPin()
            "$PKG.DEBUG_SIMULATE_AUTH" -> handleSimulateAuth(intent)
            "$PKG.DEBUG_GET_AUTH_STATE" -> handleGetAuthState()

            // --- Playback ---
            "$PKG.DEBUG_PLAY_VIDEO" -> handlePlayVideo(intent)
            "$PKG.DEBUG_STOP_PLAYBACK" -> handleStopPlayback()
            "$PKG.DEBUG_GET_NOW_PLAYING" -> handleGetNowPlaying()

            // --- Lifecycle ---
            "$PKG.DEBUG_FULL_RESET" -> handleFullReset(context)
            "$PKG.DEBUG_SIMULATE_OFFLINE" -> handleSimulateOffline()
            "$PKG.DEBUG_GET_STATE_DUMP" -> handleGetStateDump(context)
            "$PKG.DEBUG_CLEAR_PLAY_EVENTS" -> handleClearPlayEvents()
        }
    }

    private fun logResult(result: String) {
        Log.d(TAG, result)
        AppLogger.log("Intent: $result")
    }

    // --- Server/Playlists ---

    private fun handleAddPlaylist(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: run {
            logResult("""{"error":"missing url extra"}""")
            return
        }
        val playlistId = PlaylistUrlParser.parse(url) ?: run {
            logResult("""{"error":"invalid playlist url"}""")
            return
        }
        scope.launch {
            try {
                val dao = ServiceLocator.database.playlistDao()
                if (dao.getByYoutubeId(playlistId) != null) {
                    logResult("""{"error":"duplicate playlist"}""")
                    return@launch
                }
                val entity = PlaylistEntity(youtubePlaylistId = playlistId, displayName = playlistId)
                val id = dao.insert(entity)
                logResult("""{"id":$id,"youtubePlaylistId":"$playlistId"}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleRemovePlaylist(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1).toLong()
        if (id < 0) {
            logResult("""{"error":"missing id extra"}""")
            return
        }
        scope.launch {
            try {
                ServiceLocator.database.playlistDao().deleteById(id)
                logResult("""{"removed":$id}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleResolvePlaylist(intent: Intent) {
        val playlistId = intent.getStringExtra("playlist_id") ?: run {
            logResult("""{"error":"missing playlist_id extra"}""")
            return
        }
        scope.launch {
            try {
                val resolved = com.kidswatch.tv.data.PlaylistRepository.resolvePlaylist(playlistId)
                // Cache them
                com.kidswatch.tv.data.PlaylistRepository.cacheVideos(
                    ServiceLocator.database, playlistId, resolved.videos
                )
                logResult("""{"playlist_id":"$playlistId","title":"${resolved.title}","video_count":${resolved.videos.size}}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleRefreshPlaylists() {
        scope.launch {
            try {
                val playlists = ServiceLocator.database.playlistDao().getAll()
                logResult("""{"playlist_count":${playlists.size}}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleGetPlaylists() {
        scope.launch {
            try {
                val playlists = ServiceLocator.database.playlistDao().getAll()
                val arr = buildJsonArray {
                    playlists.forEach { pl ->
                        add(buildJsonObject {
                            put("id", pl.id)
                            put("youtubePlaylistId", pl.youtubePlaylistId)
                            put("displayName", pl.displayName)
                        })
                    }
                }
                logResult(json.encodeToString(arr))
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleGetServerStatus(context: Context) {
        scope.launch {
            try {
                val ip = NetworkUtils.getDeviceIp(context) ?: "unknown"
                val playlistCount = ServiceLocator.database.playlistDao().count()
                val sessions = ServiceLocator.sessionManager.getActiveSessionCount()
                logResult("""{"running":true,"port":8080,"ip":"$ip","playlists":$playlistCount,"sessions":$sessions}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    // --- PIN/Auth ---

    private fun handleGetPin() {
        val pin = ServiceLocator.pinManager.getCurrentPin()
        logResult("""{"pin":"$pin"}""")
    }

    private fun handleResetPin() {
        val newPin = ServiceLocator.pinManager.resetPin()
        ServiceLocator.sessionManager.invalidateAll()
        logResult("""{"pin":"$newPin"}""")
    }

    private fun handleSimulateAuth(intent: Intent) {
        val pin = intent.getStringExtra("pin") ?: run {
            logResult("""{"error":"missing pin extra"}""")
            return
        }
        val result = ServiceLocator.pinManager.validate(pin)
        when (result) {
            is PinResult.Success -> logResult("""{"valid":true,"token":"${result.token}"}""")
            is PinResult.Invalid -> logResult("""{"valid":false,"attemptsRemaining":${result.attemptsRemaining}}""")
            is PinResult.RateLimited -> logResult("""{"valid":false,"rateLimited":true,"retryAfterMs":${result.retryAfterMs}}""")
        }
    }

    private fun handleGetAuthState() {
        val sessions = ServiceLocator.sessionManager.getActiveSessionCount()
        val locked = ServiceLocator.pinManager.isLockedOut()
        val failed = ServiceLocator.pinManager.getFailedAttempts()
        logResult("""{"sessions":$sessions,"lockedOut":$locked,"failedAttempts":$failed}""")
    }

    // --- Playback ---

    private fun handlePlayVideo(intent: Intent) {
        val videoId = intent.getStringExtra("video_id") ?: run {
            logResult("""{"error":"missing video_id extra"}""")
            return
        }
        val playlistId = intent.getStringExtra("playlist_id") ?: ""
        onPlayVideo?.invoke(videoId, playlistId)
        logResult("""{"success":true,"video_id":"$videoId","playlist_id":"$playlistId"}""")
    }

    private fun handleStopPlayback() {
        onStopPlayback?.invoke()
        logResult("""{"success":true}""")
    }

    private fun handleGetNowPlaying() {
        val videoId = PlayEventRecorder.currentVideoId
        val playlistId = PlayEventRecorder.currentPlaylistId
        if (videoId != null) {
            logResult("""{"video_id":"$videoId","playlist_id":"${playlistId ?: ""}","playing":true}""")
        } else {
            logResult("""{"playing":false}""")
        }
    }

    // --- Lifecycle ---

    private fun handleFullReset(context: Context) {
        scope.launch {
            try {
                ServiceLocator.database.playlistDao().deleteAll()
                ServiceLocator.database.playEventDao().deleteAll()
                ServiceLocator.database.videoDao().deleteByPlaylist("%") // won't match, need deleteAll
                ServiceLocator.pinManager.resetPin()
                ServiceLocator.sessionManager.invalidateAll()
                logResult("""{"success":true}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleSimulateOffline() {
        OfflineSimulator.toggle()
        logResult("""{"offline":${OfflineSimulator.isOffline}}""")
    }

    private fun handleGetStateDump(context: Context) {
        scope.launch {
            try {
                val playlists = ServiceLocator.database.playlistDao().count()
                val events = ServiceLocator.database.playEventDao().count()
                val videos = ServiceLocator.database.videoDao().count()
                val sessions = ServiceLocator.sessionManager.getActiveSessionCount()
                val pin = ServiceLocator.pinManager.getCurrentPin()
                val ip = NetworkUtils.getDeviceIp(context) ?: "unknown"
                val offline = OfflineSimulator.isOffline
                val nowPlaying = PlayEventRecorder.currentVideoId

                logResult("""{"playlists":$playlists,"events":$events,"videos":$videos,"sessions":$sessions,"pin":"$pin","ip":"$ip","offline":$offline,"nowPlaying":${if (nowPlaying != null) "\"$nowPlaying\"" else "null"}}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleClearPlayEvents() {
        scope.launch {
            try {
                val count = ServiceLocator.database.playEventDao().count()
                ServiceLocator.database.playEventDao().deleteAll()
                logResult("""{"deleted":$count}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }
}
