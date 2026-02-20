package tv.parentapproved.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.auth.PinResult
import tv.parentapproved.app.data.ContentSourceRepository
import tv.parentapproved.app.data.cache.ChannelEntity
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.util.AppLogger
import tv.parentapproved.app.util.ContentSourceParser
import tv.parentapproved.app.util.NetworkUtils
import tv.parentapproved.app.timelimits.TimeLimitConfig
import tv.parentapproved.app.util.OfflineSimulator
import tv.parentapproved.app.util.ParseResult
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
        const val TAG = "ParentApproved-Intent"
        private const val PKG = "tv.parentapproved.app"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val json = Json { prettyPrint = false }

        // Callback for play/stop actions (set by MainActivity)
        var onPlayVideo: ((videoId: String, playlistId: String) -> Unit)? = null
        var onStopPlayback: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!tv.parentapproved.app.BuildConfig.IS_DEBUG) return

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

            // --- Relay ---
            "$PKG.DEBUG_GET_RELAY_STATUS" -> handleGetRelayStatus()
            "$PKG.DEBUG_RESET_TV_SECRET" -> handleResetTvSecret()

            // --- Time Limits ---
            "$PKG.DEBUG_SET_DAILY_LIMIT" -> handleSetDailyLimit(intent)
            "$PKG.DEBUG_CLEAR_DAILY_LIMIT" -> handleClearDailyLimit()
            "$PKG.DEBUG_MANUAL_LOCK" -> handleManualLock()
            "$PKG.DEBUG_MANUAL_UNLOCK" -> handleManualUnlock()
            "$PKG.DEBUG_GRANT_BONUS" -> handleGrantBonus(intent)
            "$PKG.DEBUG_SET_BEDTIME" -> handleSetBedtime(intent)
            "$PKG.DEBUG_CLEAR_BEDTIME" -> handleClearBedtime()
            "$PKG.DEBUG_TIME_STATUS" -> handleTimeStatus()

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
        val parseResult = ContentSourceParser.parse(url)
        if (parseResult is ParseResult.Rejected) {
            logResult("""{"error":"${parseResult.message}"}""")
            return
        }
        val source = (parseResult as ParseResult.Success).source
        scope.launch {
            try {
                val dao = ServiceLocator.database.channelDao()
                if (dao.getBySourceId(source.id) != null) {
                    logResult("""{"error":"duplicate source"}""")
                    return@launch
                }
                val entity = ChannelEntity(
                    sourceType = source.type.name.lowercase(),
                    sourceId = source.id,
                    sourceUrl = source.canonicalUrl,
                    displayName = source.id,
                )
                val id = dao.insert(entity)
                logResult("""{"id":$id,"sourceId":"${source.id}","sourceType":"${source.type.name.lowercase()}"}""")
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
                ServiceLocator.database.channelDao().deleteById(id)
                logResult("""{"removed":$id}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleResolvePlaylist(intent: Intent) {
        val sourceId = intent.getStringExtra("playlist_id") ?: run {
            logResult("""{"error":"missing playlist_id extra"}""")
            return
        }
        scope.launch {
            try {
                // Determine source type from DB or default to playlist
                val entity = ServiceLocator.database.channelDao().getBySourceId(sourceId)
                val sourceType = entity?.sourceType ?: "yt_playlist"
                val resolved = ContentSourceRepository.resolve(sourceType, sourceId)
                ContentSourceRepository.cacheVideos(ServiceLocator.database, sourceId, resolved.videos)
                logResult("""{"source_id":"$sourceId","title":"${resolved.title}","video_count":${resolved.videos.size}}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleRefreshPlaylists() {
        scope.launch {
            try {
                val channels = ServiceLocator.database.channelDao().getAll()
                logResult("""{"source_count":${channels.size}}""")
            } catch (e: Exception) {
                logResult("""{"error":"${e.message}"}""")
            }
        }
    }

    private fun handleGetPlaylists() {
        scope.launch {
            try {
                val channels = ServiceLocator.database.channelDao().getAll()
                val arr = buildJsonArray {
                    channels.forEach { ch ->
                        add(buildJsonObject {
                            put("id", ch.id)
                            put("sourceType", ch.sourceType)
                            put("sourceId", ch.sourceId)
                            put("displayName", ch.displayName)
                            put("videoCount", ch.videoCount)
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
                val sourceCount = ServiceLocator.database.channelDao().count()
                val sessions = ServiceLocator.sessionManager.getActiveSessionCount()
                logResult("""{"running":true,"port":8080,"ip":"$ip","sources":$sourceCount,"sessions":$sessions}""")
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
        // Rotate tv-secret on PIN reset (security: invalidates all remote access)
        try {
            ServiceLocator.relayConfig.rotateTvSecret()
            ServiceLocator.relayConnector.reconnectNow()
        } catch (e: Exception) {
            // Relay may not be initialized in tests
        }
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

    // --- Relay ---

    private fun handleGetRelayStatus() {
        try {
            val config = ServiceLocator.relayConfig
            val connector = ServiceLocator.relayConnector
            logResult("""{"tvId":"${config.tvId}","connected":${connector.state == tv.parentapproved.app.relay.RelayConnectionState.CONNECTED},"relayUrl":"${config.relayUrl}","state":"${connector.state}"}""")
        } catch (e: Exception) {
            logResult("""{"error":"relay not initialized"}""")
        }
    }

    private fun handleResetTvSecret() {
        try {
            ServiceLocator.relayConfig.rotateTvSecret()
            ServiceLocator.sessionManager.invalidateAll()
            ServiceLocator.relayConnector.reconnectNow()
            logResult("""{"success":true,"tvId":"${ServiceLocator.relayConfig.tvId}"}""")
        } catch (e: Exception) {
            logResult("""{"error":"${e.message}"}""")
        }
    }

    // --- Time Limits ---

    private fun handleSetDailyLimit(intent: Intent) {
        val minutes = intent.getIntExtra("minutes", -1)
        if (minutes < 0) {
            logResult("""{"error":"missing minutes extra"}""")
            return
        }
        val manager = ServiceLocator.timeLimitManager
        val config = manager.getConfig() ?: TimeLimitConfig()
        val today = java.time.LocalDate.now().dayOfWeek
        val updated = config.copy(dailyLimits = config.dailyLimits + (today to minutes))
        manager.saveConfig(updated)
        logResult("""{"success":true,"day":"${today.name.lowercase()}","minutes":$minutes}""")
    }

    private fun handleClearDailyLimit() {
        val manager = ServiceLocator.timeLimitManager
        val config = manager.getConfig() ?: TimeLimitConfig()
        manager.saveConfig(config.copy(dailyLimits = emptyMap()))
        logResult("""{"success":true}""")
    }

    private fun handleManualLock() {
        ServiceLocator.timeLimitManager.setManualLock(true)
        tv.parentapproved.app.playback.PlaybackCommandBus.send(
            tv.parentapproved.app.playback.PlaybackCommand.Stop
        )
        logResult("""{"success":true,"locked":true}""")
    }

    private fun handleManualUnlock() {
        ServiceLocator.timeLimitManager.setManualLock(false)
        logResult("""{"success":true,"locked":false}""")
    }

    private fun handleGrantBonus(intent: Intent) {
        val minutes = intent.getIntExtra("minutes", -1)
        if (minutes <= 0) {
            logResult("""{"error":"missing or invalid minutes extra"}""")
            return
        }
        ServiceLocator.timeLimitManager.grantBonusMinutes(minutes)
        logResult("""{"success":true,"minutes":$minutes}""")
    }

    private fun handleSetBedtime(intent: Intent) {
        val start = intent.getStringExtra("start") ?: run {
            logResult("""{"error":"missing start extra (HH:mm)"}""")
            return
        }
        val end = intent.getStringExtra("end") ?: run {
            logResult("""{"error":"missing end extra (HH:mm)"}""")
            return
        }
        try {
            val startParts = start.split(":")
            val endParts = end.split(":")
            val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()
            val manager = ServiceLocator.timeLimitManager
            val config = manager.getConfig() ?: TimeLimitConfig()
            manager.saveConfig(config.copy(bedtimeStartMin = startMin, bedtimeEndMin = endMin))
            logResult("""{"success":true,"start":"$start","end":"$end"}""")
        } catch (e: Exception) {
            logResult("""{"error":"invalid time format: ${e.message}"}""")
        }
    }

    private fun handleClearBedtime() {
        val manager = ServiceLocator.timeLimitManager
        val config = manager.getConfig() ?: TimeLimitConfig()
        manager.saveConfig(config.copy(bedtimeStartMin = -1, bedtimeEndMin = -1))
        logResult("""{"success":true}""")
    }

    private fun handleTimeStatus() {
        val manager = ServiceLocator.timeLimitManager
        val config = manager.getConfig()
        val status = manager.canPlay()
        val remaining = manager.getRemainingMinutes()
        val usedMin = manager.getTodayUsedMinutes()
        logResult("""{"status":"$status","remaining":${remaining ?: "null"},"usedMin":$usedMin,"manuallyLocked":${config?.manuallyLocked ?: false},"bonusMinutes":${config?.bonusMinutes ?: 0},"bonusDate":"${config?.bonusDate ?: ""}"}""")
    }

    // --- Lifecycle ---

    private fun handleFullReset(context: Context) {
        scope.launch {
            try {
                ServiceLocator.database.channelDao().deleteAll()
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
                val sources = ServiceLocator.database.channelDao().count()
                val events = ServiceLocator.database.playEventDao().count()
                val videos = ServiceLocator.database.videoDao().count()
                val sessions = ServiceLocator.sessionManager.getActiveSessionCount()
                val pin = ServiceLocator.pinManager.getCurrentPin()
                val ip = NetworkUtils.getDeviceIp(context) ?: "unknown"
                val offline = OfflineSimulator.isOffline
                val nowPlaying = PlayEventRecorder.currentVideoId

                logResult("""{"sources":$sources,"events":$events,"videos":$videos,"sessions":$sessions,"pin":"$pin","ip":"$ip","offline":$offline,"nowPlaying":${if (nowPlaying != null) "\"$nowPlaying\"" else "null"}}""")
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
