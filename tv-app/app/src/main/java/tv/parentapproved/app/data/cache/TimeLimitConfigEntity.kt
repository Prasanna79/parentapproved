package tv.parentapproved.app.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_limit_config")
data class TimeLimitConfigEntity(
    @PrimaryKey val id: Int = 1, // singleton row
    // Daily limits in minutes (-1 = no limit)
    val mondayLimitMin: Int = -1,
    val tuesdayLimitMin: Int = -1,
    val wednesdayLimitMin: Int = -1,
    val thursdayLimitMin: Int = -1,
    val fridayLimitMin: Int = -1,
    val saturdayLimitMin: Int = -1,
    val sundayLimitMin: Int = -1,
    // Bedtime: minutes from midnight (-1 = off)
    val bedtimeStartMin: Int = -1,
    val bedtimeEndMin: Int = -1,
    // Manual lock
    val manuallyLocked: Boolean = false,
    // Bonus time for today
    val bonusMinutes: Int = 0,
    val bonusDate: String = "",
)
