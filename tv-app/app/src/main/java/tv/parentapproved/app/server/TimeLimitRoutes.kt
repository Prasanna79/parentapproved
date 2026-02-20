package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.playback.PlaybackCommand
import tv.parentapproved.app.playback.PlaybackCommandBus
import tv.parentapproved.app.timelimits.LockReason
import tv.parentapproved.app.timelimits.TimeLimitManager
import tv.parentapproved.app.timelimits.TimeLimitStatus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

@Serializable
data class TimeLimitResponse(
    val dailyLimits: Map<String, Int>,
    val bedtime: BedtimeResponse?,
    val todayLimitMin: Int?,
    val todayUsedMin: Int,
    val todayBonusMin: Int,
    val todayRemainingMin: Int?,
    val manuallyLocked: Boolean,
    val currentStatus: String,
    val lockReason: String?,
    val hasTimeRequest: Boolean = false,
)

@Serializable
data class BedtimeResponse(
    val start: String,
    val end: String,
)

@Serializable
data class TimeLimitUpdateRequest(
    val dailyLimits: Map<String, Int>? = null,
    val bedtimeStartMin: Int? = null,
    val bedtimeEndMin: Int? = null,
)

@Serializable
data class LockRequest(val locked: Boolean)

@Serializable
data class BonusRequest(val minutes: Int)

@Serializable
data class BonusResponse(val success: Boolean, val remainingMin: Int?)

fun Route.timeLimitRoutes(sessionManager: SessionManager, timeLimitManager: TimeLimitManager) {
    var hasTimeRequest = false
    var lastRequestTime = 0L

    get("/time-limits") {
        if (!validateSession(sessionManager)) return@get

        val config = timeLimitManager.getConfig()
        val status = timeLimitManager.canPlay()
        val remaining = timeLimitManager.getRemainingMinutes()
        val usedMin = timeLimitManager.getTodayUsedMinutes()

        val dailyLimits = config?.dailyLimits?.mapKeys { it.key.name.lowercase() } ?: emptyMap()
        val bedtime = if (config != null && config.bedtimeStartMin >= 0 && config.bedtimeEndMin >= 0) {
            BedtimeResponse(
                start = formatMinutes(config.bedtimeStartMin),
                end = formatMinutes(config.bedtimeEndMin),
            )
        } else null

        val todayLimit = config?.dailyLimits?.get(java.time.LocalDate.now().dayOfWeek)
        val todayBonus = config?.let {
            if (it.bonusDate == java.time.LocalDate.now().toString()) it.bonusMinutes else 0
        } ?: 0

        call.respond(TimeLimitResponse(
            dailyLimits = dailyLimits,
            bedtime = bedtime,
            todayLimitMin = todayLimit,
            todayUsedMin = usedMin,
            todayBonusMin = todayBonus,
            todayRemainingMin = remaining,
            manuallyLocked = config?.manuallyLocked ?: false,
            currentStatus = when (status) {
                is TimeLimitStatus.Allowed -> "allowed"
                is TimeLimitStatus.Warning -> "warning"
                is TimeLimitStatus.Blocked -> "blocked"
            },
            lockReason = when (status) {
                is TimeLimitStatus.Blocked -> status.reason.name.lowercase()
                else -> null
            },
            hasTimeRequest = hasTimeRequest,
        ))
    }

    put("/time-limits") {
        if (!validateSession(sessionManager)) return@put

        val body = try {
            call.receive<TimeLimitUpdateRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            return@put
        }

        // Validate limits
        body.dailyLimits?.values?.forEach { limit ->
            if (limit < -1 || limit > 480) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Daily limit must be -1 to 480 minutes"))
                return@put
            }
        }

        val existing = timeLimitManager.getConfig() ?: tv.parentapproved.app.timelimits.TimeLimitConfig()

        val newDailyLimits = if (body.dailyLimits != null) {
            body.dailyLimits.mapKeys { (key, _) ->
                DayOfWeek.valueOf(key.uppercase())
            }
        } else existing.dailyLimits

        val updated = existing.copy(
            dailyLimits = newDailyLimits,
            bedtimeStartMin = body.bedtimeStartMin ?: existing.bedtimeStartMin,
            bedtimeEndMin = body.bedtimeEndMin ?: existing.bedtimeEndMin,
        )

        timeLimitManager.saveConfig(updated)
        call.respond(HttpStatusCode.OK, mapOf("success" to "true"))
    }

    post("/time-limits/lock") {
        if (!validateSession(sessionManager)) return@post

        val body = try {
            call.receive<LockRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            return@post
        }

        timeLimitManager.setManualLock(body.locked)
        if (body.locked) {
            PlaybackCommandBus.send(PlaybackCommand.Stop)
        }
        call.respond(HttpStatusCode.OK, mapOf("success" to "true"))
    }

    post("/time-limits/bonus") {
        if (!validateSession(sessionManager)) return@post

        val body = try {
            call.receive<BonusRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            return@post
        }

        if (body.minutes <= 0) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Minutes must be positive"))
            return@post
        }
        if (body.minutes > 240) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Maximum bonus is 240 minutes"))
            return@post
        }

        timeLimitManager.grantBonusMinutes(body.minutes)
        hasTimeRequest = false // Clear any pending request

        val remaining = timeLimitManager.getRemainingMinutes()
        call.respond(HttpStatusCode.OK, BonusResponse(success = true, remainingMin = remaining))
    }

    post("/time-limits/request") {
        // Unauthenticated — comes from TV lock screen
        val now = System.currentTimeMillis()
        if (now - lastRequestTime < 2 * 60 * 1000) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Please wait before requesting again"))
            return@post
        }
        hasTimeRequest = true
        lastRequestTime = now
        call.respond(HttpStatusCode.OK, mapOf("success" to "true"))
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "%02d:%02d".format(hours, minutes)
}
