package tv.parentapproved.app.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TimeLimitDao {
    @Query("SELECT * FROM time_limit_config WHERE id = 1")
    suspend fun getConfig(): TimeLimitConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: TimeLimitConfigEntity)

    @Query("UPDATE time_limit_config SET manuallyLocked = :locked WHERE id = 1")
    suspend fun setManualLock(locked: Boolean)

    @Query("UPDATE time_limit_config SET bonusMinutes = :minutes, bonusDate = :date WHERE id = 1")
    suspend fun updateBonus(minutes: Int, date: String)
}
