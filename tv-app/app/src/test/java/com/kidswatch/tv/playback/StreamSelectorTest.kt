package com.kidswatch.tv.playback

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class StreamSelectorTest {

    private fun videoStream(resolution: String, url: String, isVideoOnly: Boolean = false): VideoStream {
        val builder = VideoStream.Builder()
            .setId("id-$resolution")
            .setContent(url, true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution(resolution)
            .setIsVideoOnly(isVideoOnly)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        return builder.build()
    }

    private fun audioStream(bitrate: Int, url: String): AudioStream {
        val builder = AudioStream.Builder()
            .setId("audio-$bitrate")
            .setContent(url, true)
            .setMediaFormat(MediaFormat.M4A)
            .setAverageBitrate(bitrate)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        return builder.build()
    }

    @Test
    fun selectBest_prefers1080pProgressive() {
        val prog = listOf(
            videoStream("720p", "http://720"),
            videoStream("1080p", "http://1080"),
        )
        val result = StreamSelector.selectBest(prog, emptyList(), emptyList())
        assertNotNull(result)
        assertEquals("http://1080", result!!.videoUrl)
        assertNull(result.audioUrl)
        assertFalse(result.isMerged)
    }

    @Test
    fun selectBest_prefers720pIfNo1080p() {
        val prog = listOf(
            videoStream("360p", "http://360"),
            videoStream("720p", "http://720"),
        )
        val result = StreamSelector.selectBest(prog, emptyList(), emptyList())
        assertNotNull(result)
        assertEquals("http://720", result!!.videoUrl)
    }

    @Test
    fun selectBest_fallsBackToAnyProgressive() {
        val prog = listOf(
            videoStream("360p", "http://360"),
        )
        val result = StreamSelector.selectBest(prog, emptyList(), emptyList())
        assertNotNull(result)
        assertEquals("http://360", result!!.videoUrl)
    }

    @Test
    fun selectBest_fallsBackToAdaptiveMerge() {
        val videoOnly = listOf(
            videoStream("720p", "http://v720", isVideoOnly = true),
        )
        val audio = listOf(
            audioStream(128, "http://a128"),
        )
        val result = StreamSelector.selectBest(emptyList(), videoOnly, audio)
        assertNotNull(result)
        assertEquals("http://v720", result!!.videoUrl)
        assertEquals("http://a128", result.audioUrl)
        assertTrue(result.isMerged)
    }

    @Test
    fun selectBest_adaptiveCapsAt1080p() {
        val videoOnly = listOf(
            videoStream("2160p", "http://v4k", isVideoOnly = true),
            videoStream("1080p", "http://v1080", isVideoOnly = true),
        )
        val audio = listOf(audioStream(128, "http://audio"))
        val result = StreamSelector.selectBest(emptyList(), videoOnly, audio)
        assertNotNull(result)
        assertEquals("http://v1080", result!!.videoUrl)
    }

    @Test
    fun selectBest_noStreamsReturnsNull() {
        val result = StreamSelector.selectBest(emptyList(), emptyList(), emptyList())
        assertNull(result)
    }

    @Test
    fun selectBest_selectsHighestBitrateAudio() {
        val videoOnly = listOf(
            videoStream("720p", "http://v720", isVideoOnly = true),
        )
        val audio = listOf(
            audioStream(64, "http://a64"),
            audioStream(256, "http://a256"),
            audioStream(128, "http://a128"),
        )
        val result = StreamSelector.selectBest(emptyList(), videoOnly, audio)
        assertNotNull(result)
        assertEquals("http://a256", result!!.audioUrl)
    }
}
