package com.kidswatch.tv.data.events

import com.google.firebase.firestore.FieldValue
import com.kidswatch.tv.data.FirebaseManager
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

object PlayEventRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var db: CacheDatabase? = null
    private var currentEventId: Long? = null
    private var currentStartTime: Long = 0
    private var familyId: String? = null

    fun init(database: CacheDatabase, familyId: String?) {
        this.db = database
        this.familyId = familyId
    }

    fun startEvent(videoId: String, playlistId: String) {
        val dao = db?.playEventDao() ?: return
        currentStartTime = System.currentTimeMillis()
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
            val events = dao.getUnflushed()
            val event = events.find { it.id == eventId } ?: return@launch
            dao.update(event.copy(durationSec = durationSec, completedPct = completedPct))
        }
    }

    fun endEvent(durationSec: Int, completedPct: Int) {
        updateEvent(durationSec, completedPct)
        currentEventId = null
    }

    fun flush() {
        val dao = db?.playEventDao() ?: return
        scope.launch {
            try {
                val unflushed = dao.getUnflushed()
                if (unflushed.isEmpty()) return@launch

                val events = unflushed.map { e ->
                    mapOf(
                        "device_uid" to (FirebaseManager.uid ?: ""),
                        "family_id" to (familyId ?: ""),
                        "video_id" to e.videoId,
                        "playlist_id" to e.playlistId,
                        "started_at" to Date(e.startedAt),
                        "duration_sec" to e.durationSec,
                        "completed_pct" to e.completedPct,
                    )
                }

                FirebaseManager.writePlayEvents(events) { success ->
                    if (success) {
                        scope.launch {
                            dao.markFlushed(unflushed.map { it.id })
                            dao.deleteFlushed()
                            AppLogger.success("Flushed ${unflushed.size} play events")
                        }
                    } else {
                        AppLogger.warn("Flush failed, will retry later")
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("Flush error: ${e.message}")
            }
        }
    }

    fun pendingCount(onResult: (Int) -> Unit) {
        val dao = db?.playEventDao() ?: return onResult(0)
        scope.launch {
            val count = dao.unflushedCount()
            onResult(count)
        }
    }

    fun clearAll() {
        val dao = db?.playEventDao() ?: return
        scope.launch { dao.deleteAll() }
    }
}
