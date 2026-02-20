package tv.parentapproved.app.timelimits

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimeLimitManager(
    private val clock: () -> Long = System::currentTimeMillis,
    private val store: TimeLimitStore,
    private val watchTimeProvider: WatchTimeProvider,
) {
    companion object {
        private const val WARNING_THRESHOLD_MINUTES = 5
        private const val MAX_BONUS_MINUTES = 240
    }

    suspend fun canPlay(): TimeLimitStatus {
        val config = store.getConfig() ?: return TimeLimitStatus.Allowed

        // Priority 1: Manual lock (nothing overrides this)
        if (config.manuallyLocked) {
            return TimeLimitStatus.Blocked(LockReason.MANUAL_LOCK)
        }

        val now = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault())
        val today = now.toLocalDate()
        val todayStr = today.toString()

        // Effective bonus for today (resets if date changed)
        val effectiveBonus = if (config.bonusDate == todayStr) config.bonusMinutes else 0

        // Priority 2: Daily limit check
        val dayOfWeek = now.dayOfWeek
        val dailyLimitMin = config.dailyLimits[dayOfWeek]

        if (dailyLimitMin != null && dailyLimitMin >= 0) {
            val effectiveLimitMin = dailyLimitMin + effectiveBonus
            val usedMin = watchTimeProvider.getTodayWatchSeconds() / 60

            if (usedMin >= effectiveLimitMin) {
                return TimeLimitStatus.Blocked(LockReason.DAILY_LIMIT)
            }

            val remainingMin = effectiveLimitMin - usedMin
            if (remainingMin <= WARNING_THRESHOLD_MINUTES) {
                return TimeLimitStatus.Warning(remainingMin)
            }
        }

        // Priority 3: Bedtime check
        if (isBedtime(config, now.toLocalTime())) {
            // Bonus overrides bedtime
            if (effectiveBonus > 0) {
                // During bedtime with bonus: bonus acts as a temporary daily limit
                val usedMin = watchTimeProvider.getTodayWatchSeconds() / 60
                if (usedMin >= effectiveBonus) {
                    return TimeLimitStatus.Blocked(LockReason.BEDTIME)
                }
                val remainingMin = effectiveBonus - usedMin
                if (remainingMin <= WARNING_THRESHOLD_MINUTES) {
                    return TimeLimitStatus.Warning(remainingMin)
                }
                return TimeLimitStatus.Allowed
            }
            return TimeLimitStatus.Blocked(LockReason.BEDTIME)
        }

        return TimeLimitStatus.Allowed
    }

    suspend fun getRemainingMinutes(): Int? {
        val config = store.getConfig() ?: return null

        val now = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault())
        val todayStr = now.toLocalDate().toString()
        val effectiveBonus = if (config.bonusDate == todayStr) config.bonusMinutes else 0
        val dayOfWeek = now.dayOfWeek
        val dailyLimitMin = config.dailyLimits[dayOfWeek] ?: return null

        if (dailyLimitMin < 0) return null

        val effectiveLimitMin = dailyLimitMin + effectiveBonus
        val usedMin = watchTimeProvider.getTodayWatchSeconds() / 60
        return (effectiveLimitMin - usedMin).coerceAtLeast(0)
    }

    suspend fun setManualLock(locked: Boolean) {
        if (locked) {
            // Locking clears bonus — parent is saying "no more watching"
            store.updateBonus(0, "")
        }
        store.updateManualLock(locked)
    }

    suspend fun grantBonusMinutes(minutes: Int) {
        val config = store.getConfig() ?: TimeLimitConfig()
        val now = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault())
        val todayStr = now.toLocalDate().toString()

        val currentBonus = if (config.bonusDate == todayStr) config.bonusMinutes else 0
        val newBonus = (currentBonus + minutes).coerceAtMost(MAX_BONUS_MINUTES)
        store.updateBonus(newBonus, todayStr)
    }

    suspend fun getConfig(): TimeLimitConfig? = store.getConfig()

    suspend fun saveConfig(config: TimeLimitConfig) = store.saveConfig(config)

    suspend fun getTodayUsedMinutes(): Int = watchTimeProvider.getTodayWatchSeconds() / 60

    suspend fun isManuallyLocked(): Boolean = store.getConfig()?.manuallyLocked == true

    private fun isBedtime(config: TimeLimitConfig, currentTime: LocalTime): Boolean {
        val start = config.bedtimeStartMin
        val end = config.bedtimeEndMin

        if (start < 0 || end < 0) return false

        val currentMin = currentTime.hour * 60 + currentTime.minute

        return if (start <= end) {
            // Same-day bedtime (e.g., 13:00-17:00) — unusual but supported
            currentMin in start until end
        } else {
            // Spans midnight (e.g., 20:30-07:00)
            currentMin >= start || currentMin < end
        }
    }
}
