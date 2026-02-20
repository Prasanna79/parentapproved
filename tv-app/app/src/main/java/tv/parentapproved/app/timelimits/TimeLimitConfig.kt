package tv.parentapproved.app.timelimits

import java.time.DayOfWeek

/**
 * Time limit configuration. All times in minutes.
 * -1 means "no limit" / "off" for that field.
 */
data class TimeLimitConfig(
    val dailyLimits: Map<DayOfWeek, Int> = emptyMap(), // minutes per day, -1 = no limit
    val bedtimeStartMin: Int = -1, // minutes from midnight (e.g., 20:30 = 1230)
    val bedtimeEndMin: Int = -1,   // minutes from midnight (e.g., 07:00 = 420)
    val manuallyLocked: Boolean = false,
    val bonusMinutes: Int = 0,
    val bonusDate: String = "",    // ISO date (yyyy-MM-dd), resets when day changes
)

sealed class TimeLimitStatus {
    object Allowed : TimeLimitStatus()
    data class Warning(val minutesLeft: Int) : TimeLimitStatus()
    data class Blocked(val reason: LockReason) : TimeLimitStatus()
}

enum class LockReason {
    DAILY_LIMIT,
    BEDTIME,
    MANUAL_LOCK,
}

interface TimeLimitStore {
    fun getConfig(): TimeLimitConfig?
    fun saveConfig(config: TimeLimitConfig)
    fun updateManualLock(locked: Boolean)
    fun updateBonus(minutes: Int, date: String)
}

interface WatchTimeProvider {
    fun getTodayWatchSeconds(): Int
}
