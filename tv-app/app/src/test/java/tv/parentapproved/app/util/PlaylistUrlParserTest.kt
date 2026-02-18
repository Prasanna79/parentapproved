package tv.parentapproved.app.util

import org.junit.Assert.*
import org.junit.Test

class PlaylistUrlParserTest {

    @Test
    fun parse_standardUrl_extractsId() {
        val result = PlaylistUrlParser.parse("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", result)
    }

    @Test
    fun parse_mobileUrl_extractsId() {
        val result = PlaylistUrlParser.parse("https://m.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", result)
    }

    @Test
    fun parse_urlWithExtraParams_extractsId() {
        val result = PlaylistUrlParser.parse("https://www.youtube.com/playlist?list=PLtest123&index=5")
        assertEquals("PLtest123", result)
    }

    @Test
    fun parse_videoUrlWithListParam_extractsId() {
        val result = PlaylistUrlParser.parse("https://www.youtube.com/watch?v=abc123&list=PLxyz789")
        assertEquals("PLxyz789", result)
    }

    @Test
    fun parse_shortUrl_returnsNull() {
        val result = PlaylistUrlParser.parse("https://youtu.be/dQw4w9WgXcQ")
        assertNull(result)
    }

    @Test
    fun parse_videoUrl_returnsNull() {
        val result = PlaylistUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNull(result)
    }

    @Test
    fun parse_nonYoutubeUrl_returnsNull() {
        val result = PlaylistUrlParser.parse("https://vimeo.com/12345")
        assertNull(result)
    }

    @Test
    fun parse_barePlaylistId_extractsId() {
        val result = PlaylistUrlParser.parse("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", result)
    }
}
