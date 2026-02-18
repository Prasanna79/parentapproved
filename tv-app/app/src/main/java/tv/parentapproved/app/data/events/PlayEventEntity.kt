package tv.parentapproved.app.data.events

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_events")
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val playlistId: String,
    val startedAt: Long,
    val durationSec: Int = 0,
    val completedPct: Int = 0,
)
