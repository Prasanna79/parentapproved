package tv.parentapproved.app.timelimits

import kotlinx.coroutines.runBlocking
import tv.parentapproved.app.data.cache.TimeLimitConfigEntity
import tv.parentapproved.app.data.cache.TimeLimitDao
import java.time.DayOfWeek

/**
 * Bridges TimeLimitStore interface to Room DAO.
 * Uses runBlocking since TimeLimitManager.canPlay() is called synchronously
 * from the UI thread's periodic check. The Room queries are fast (single-row reads).
 */
class RoomTimeLimitStore(
    private val dao: TimeLimitDao,
) : TimeLimitStore {

    override fun getConfig(): TimeLimitConfig? = runBlocking {
        dao.getConfig()?.toTimeLimitConfig()
    }

    override fun saveConfig(config: TimeLimitConfig) = runBlocking {
        dao.insertOrUpdate(config.toEntity())
    }

    override fun updateManualLock(locked: Boolean) = runBlocking {
        // Ensure row exists
        if (dao.getConfig() == null) {
            dao.insertOrUpdate(TimeLimitConfigEntity())
        }
        dao.setManualLock(locked)
    }

    override fun updateBonus(minutes: Int, date: String) = runBlocking {
        if (dao.getConfig() == null) {
            dao.insertOrUpdate(TimeLimitConfigEntity())
        }
        dao.updateBonus(minutes, date)
    }
}

private fun TimeLimitConfigEntity.toTimeLimitConfig(): TimeLimitConfig {
    val limits = mutableMapOf<DayOfWeek, Int>()
    if (mondayLimitMin >= 0) limits[DayOfWeek.MONDAY] = mondayLimitMin
    if (tuesdayLimitMin >= 0) limits[DayOfWeek.TUESDAY] = tuesdayLimitMin
    if (wednesdayLimitMin >= 0) limits[DayOfWeek.WEDNESDAY] = wednesdayLimitMin
    if (thursdayLimitMin >= 0) limits[DayOfWeek.THURSDAY] = thursdayLimitMin
    if (fridayLimitMin >= 0) limits[DayOfWeek.FRIDAY] = fridayLimitMin
    if (saturdayLimitMin >= 0) limits[DayOfWeek.SATURDAY] = saturdayLimitMin
    if (sundayLimitMin >= 0) limits[DayOfWeek.SUNDAY] = sundayLimitMin

    return TimeLimitConfig(
        dailyLimits = limits,
        bedtimeStartMin = bedtimeStartMin,
        bedtimeEndMin = bedtimeEndMin,
        manuallyLocked = manuallyLocked,
        bonusMinutes = bonusMinutes,
        bonusDate = bonusDate,
    )
}

private fun TimeLimitConfig.toEntity(): TimeLimitConfigEntity {
    return TimeLimitConfigEntity(
        mondayLimitMin = dailyLimits[DayOfWeek.MONDAY] ?: -1,
        tuesdayLimitMin = dailyLimits[DayOfWeek.TUESDAY] ?: -1,
        wednesdayLimitMin = dailyLimits[DayOfWeek.WEDNESDAY] ?: -1,
        thursdayLimitMin = dailyLimits[DayOfWeek.THURSDAY] ?: -1,
        fridayLimitMin = dailyLimits[DayOfWeek.FRIDAY] ?: -1,
        saturdayLimitMin = dailyLimits[DayOfWeek.SATURDAY] ?: -1,
        sundayLimitMin = dailyLimits[DayOfWeek.SUNDAY] ?: -1,
        bedtimeStartMin = bedtimeStartMin,
        bedtimeEndMin = bedtimeEndMin,
        manuallyLocked = manuallyLocked,
        bonusMinutes = bonusMinutes,
        bonusDate = bonusDate,
    )
}
