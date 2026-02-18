package tv.parentapproved.app.playback

import tv.parentapproved.app.util.AppLogger
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

data class SelectedStream(
    val videoUrl: String,
    val audioUrl: String?,
    val resolution: String,
    val isMerged: Boolean,
)

object StreamSelector {

    /**
     * Priority: 1080p progressive > 720p progressive > any progressive > adaptive merge
     */
    fun selectBest(
        progressiveStreams: List<VideoStream>,
        videoOnlyStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
    ): SelectedStream? {
        // Try progressive first (has audio built in)
        val progressive = progressiveStreams
            .sortedByDescending { resolutionToInt(it.resolution) }

        // Try 1080p progressive
        progressive.find { resolutionToInt(it.resolution) == 1080 }?.let { stream ->
            AppLogger.log("Selected: 1080p progressive")
            return SelectedStream(stream.content, null, stream.resolution ?: "1080p", false)
        }

        // Try 720p progressive
        progressive.find { resolutionToInt(it.resolution) == 720 }?.let { stream ->
            AppLogger.log("Selected: 720p progressive")
            return SelectedStream(stream.content, null, stream.resolution ?: "720p", false)
        }

        // Any progressive
        progressive.firstOrNull()?.let { stream ->
            AppLogger.log("Selected: ${stream.resolution} progressive")
            return SelectedStream(stream.content, null, stream.resolution ?: "?", false)
        }

        // Fallback: merge video-only + audio
        val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
        val bestVideo = videoOnlyStreams
            .sortedByDescending { resolutionToInt(it.resolution) }
            .firstOrNull { resolutionToInt(it.resolution) <= 1080 }

        if (bestVideo != null && bestAudio != null) {
            AppLogger.log("Selected: ${bestVideo.resolution} merged + audio ${bestAudio.averageBitrate}kbps")
            return SelectedStream(bestVideo.content, bestAudio.content, bestVideo.resolution ?: "?", true)
        }

        AppLogger.error("No playable streams found")
        return null
    }

    private fun resolutionToInt(resolution: String?): Int {
        return resolution?.replace("p", "")?.toIntOrNull() ?: 0
    }
}
