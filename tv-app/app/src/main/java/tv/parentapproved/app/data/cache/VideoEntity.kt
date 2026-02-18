package tv.parentapproved.app.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val videoId: String,
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val position: Int,
)
