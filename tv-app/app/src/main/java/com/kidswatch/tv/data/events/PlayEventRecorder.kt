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
    private var clock: () -> Long = { System.currentTimeMillis() }
    private var pausedElapsedMs: Long = 0

    var currentVideoId: String? = null
        private set
    var currentPlaylistId: String? = null
        private set
    var currentTitle: String? = null
        private set
    var currentPlaylistTitle: String? = null
        private set
    var currentDurationMs: Long = 0
        private set
    var isPlaying: Boolean = false
        private set

    fun init(database: CacheDatabase, clock: () -> Long = { System.currentTimeMillis() }) {
        this.db = database
        this.clock = clock
    }

    fun startEvent(videoId: String, playlistId: String, title: String = "", playlistTitle: String = "", durationMs: Long = 0) {
        val dao = db?.playEventDao() ?: return
        currentStartTime = clock()
        currentVideoId = videoId
        currentPlaylistId = playlistId
        currentTitle = title
        currentPlaylistTitle = playlistTitle
        currentDurationMs = durationMs
        isPlaying = true
        pausedElapsedMs = 0
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

    fun onPause() {
        if (isPlaying) {
            pausedElapsedMs = clock() - currentStartTime
            isPlaying = false
        }
    }

    fun onResume() {
        if (!isPlaying && currentVideoId != null) {
            currentStartTime = clock() - pausedElapsedMs
            isPlaying = true
        }
    }

    fun getElapsedMs(): Long {
        return if (isPlaying) {
            clock() - currentStartTime
        } else {
            pausedElapsedMs
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
        currentTitle = null
        currentPlaylistTitle = null
        currentDurationMs = 0
        isPlaying = false
        pausedElapsedMs = 0
    }

    fun clearAll() {
        val dao = db?.playEventDao() ?: return
        scope.launch { dao.deleteAll() }
    }
}
