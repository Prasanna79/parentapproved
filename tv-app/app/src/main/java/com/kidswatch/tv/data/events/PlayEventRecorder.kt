package com.kidswatch.tv.data.events

import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PlayEventRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var db: CacheDatabase? = null
    private var currentEventId: Long? = null
    private var currentStartTime: Long = 0

    var currentVideoId: String? = null
        private set
    var currentPlaylistId: String? = null
        private set

    fun init(database: CacheDatabase) {
        this.db = database
    }

    fun startEvent(videoId: String, playlistId: String) {
        val dao = db?.playEventDao() ?: return
        currentStartTime = System.currentTimeMillis()
        currentVideoId = videoId
        currentPlaylistId = playlistId
        scope.launch {
            val event = PlayEventEntity(
                videoId = videoId,
                playlistId = playlistId,
                startedAt = currentStartTime,
            )
            currentEventId = dao.insert(event)
            AppLogger.log("Play event started: $videoId")
        }
    }

    fun updateEvent(durationSec: Int, completedPct: Int) {
        val dao = db?.playEventDao() ?: return
        val eventId = currentEventId ?: return
        scope.launch {
            val event = dao.getById(eventId) ?: return@launch
            dao.update(event.copy(durationSec = durationSec, completedPct = completedPct))
        }
    }

    fun endEvent(durationSec: Int, completedPct: Int) {
        updateEvent(durationSec, completedPct)
        currentEventId = null
        currentVideoId = null
        currentPlaylistId = null
    }

    fun clearAll() {
        val dao = db?.playEventDao() ?: return
        scope.launch { dao.deleteAll() }
    }
}
