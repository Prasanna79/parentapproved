package tv.parentapproved.app.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [Index(value = ["source_id"], unique = true)]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_type") val sourceType: String, // "yt_playlist", "yt_video", "yt_channel"
    @ColumnInfo(name = "source_id") val sourceId: String,     // PL..., video ID, UC..., @handle, etc.
    @ColumnInfo(name = "source_url") val sourceUrl: String,   // canonical URL
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "video_count") val videoCount: Int = 0,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    val status: String = "active",
)
