package tv.parentapproved.app.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ContentSourceRepository pure functions.
 * Network-dependent methods (resolve) are not tested here.
 */
class ContentSourceRepositoryTest {

    @Test
    fun buildCanonicalUrl_ytPlaylist() {
        val url = ContentSourceRepository.buildCanonicalUrl("yt_playlist", "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertEquals("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", url)
    }

    @Test
    fun buildCanonicalUrl_ytVideo() {
        val url = ContentSourceRepository.buildCanonicalUrl("yt_video", "dQw4w9WgXcQ")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", url)
    }

    @Test
    fun buildCanonicalUrl_ytChannel_UCprefix() {
        val url = ContentSourceRepository.buildCanonicalUrl("yt_channel", "UCxxxxxxxxxxxxxx")
        assertEquals("https://www.youtube.com/channel/UCxxxxxxxxxxxxxx", url)
    }

    @Test
    fun buildCanonicalUrl_ytChannel_handle() {
        val url = ContentSourceRepository.buildCanonicalUrl("yt_channel", "@CoolChannel")
        assertEquals("https://www.youtube.com/@CoolChannel", url)
    }

    @Test
    fun buildCanonicalUrl_ytChannel_customPath() {
        val url = ContentSourceRepository.buildCanonicalUrl("yt_channel", "c/CustomName")
        assertEquals("https://www.youtube.com/c/CustomName", url)
    }

    @Test
    fun buildCanonicalUrl_ytChannel_userPath() {
        val url = ContentSourceRepository.buildCanonicalUrl("yt_channel", "user/SomeUser")
        assertEquals("https://www.youtube.com/user/SomeUser", url)
    }

    @Test
    fun buildCanonicalUrl_unknownType_returnsSourceId() {
        val url = ContentSourceRepository.buildCanonicalUrl("unknown_type", "some-id")
        assertEquals("some-id", url)
    }

    @Test
    fun buildCanonicalUrl_ytChannel_plainName() {
        val url = ContentSourceRepository.buildCanonicalUrl("yt_channel", "SomeChannelName")
        assertEquals("https://www.youtube.com/SomeChannelName", url)
    }
}
