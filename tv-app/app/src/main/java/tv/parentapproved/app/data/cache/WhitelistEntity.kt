package tv.parentapproved.app.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_whitelist",
    indices = [Index(value = ["package_name"], unique = true)]
)
data class WhitelistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val whitelisted: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
)
