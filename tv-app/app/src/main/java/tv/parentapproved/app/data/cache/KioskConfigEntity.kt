package tv.parentapproved.app.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kiosk_config")
data class KioskConfigEntity(
    @PrimaryKey val id: Int = 1, // singleton row
    val kioskEnabled: Boolean = false,
    val enforceTimeLimitsOnAllApps: Boolean = false,
)
