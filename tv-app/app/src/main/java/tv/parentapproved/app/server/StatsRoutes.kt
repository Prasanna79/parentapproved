package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.TimeZone

@Serializable
data class StatsResponse(
    val totalEventsToday: Int,
    val totalWatchTimeToday: Int,
    val totalEventsAllTime: Int,
)

@Serializable
data class RecentEventResponse(
    val id: Long,
    val videoId: String,
    val playlistId: String,
    val startedAt: Long,
    val durationSec: Int,
    val completedPct: Int,
)

fun Route.statsRoutes(sessionManager: SessionManager, database: CacheDatabase) {
    get("/stats") {
        if (!validateSession(sessionManager)) return@get

        val dao = database.playEventDao()
        val todayStart = todayStartMillis()
        val todayEvents = dao.getForToday(todayStart)
        val todayWatchTime = dao.sumDurationToday(todayStart)
        val totalEvents = dao.count()

        call.respond(StatsResponse(
            totalEventsToday = todayEvents.size,
            totalWatchTimeToday = todayWatchTime,
            totalEventsAllTime = totalEvents,
        ))
    }

    get("/stats/recent") {
        if (!validateSession(sessionManager)) return@get

        val dao = database.playEventDao()
        val recent = dao.getRecent(20)
        call.respond(recent.map {
            RecentEventResponse(
                id = it.id,
                videoId = it.videoId,
                playlistId = it.playlistId,
                startedAt = it.startedAt,
                durationSec = it.durationSec,
                completedPct = it.completedPct,
            )
        })
    }
}

private fun todayStartMillis(): Long {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
