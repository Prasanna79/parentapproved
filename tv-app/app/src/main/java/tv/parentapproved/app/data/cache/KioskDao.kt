package tv.parentapproved.app.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface KioskDao {
    @Query("SELECT * FROM kiosk_config WHERE id = 1")
    suspend fun getConfig(): KioskConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: KioskConfigEntity)

    @Query("UPDATE kiosk_config SET kioskEnabled = :enabled WHERE id = 1")
    suspend fun setKioskEnabled(enabled: Boolean)

    @Query("UPDATE kiosk_config SET enforceTimeLimitsOnAllApps = :enforce WHERE id = 1")
    suspend fun setEnforceTimeLimitsOnAllApps(enforce: Boolean)
}
