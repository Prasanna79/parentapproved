package tv.parentapproved.app.data.models

data class VideoItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val playlistId: String,
    val position: Int,
)
