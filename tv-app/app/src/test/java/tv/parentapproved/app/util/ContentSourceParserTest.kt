package tv.parentapproved.app.util

import org.junit.Assert.*
import org.junit.Test

class ContentSourceParserTest {

    private fun assertSuccess(fixture: TestUrl) {
        val result = ContentSourceParser.parse(fixture.input)
        assertTrue(
            "Expected Success for '${fixture.description}' (${fixture.input}), got: $result",
            result is ParseResult.Success,
        )
        val source = (result as ParseResult.Success).source
        val expectedType = when (fixture.expectedType) {
            "yt_playlist" -> SourceType.YT_PLAYLIST
            "yt_video" -> SourceType.YT_VIDEO
            "yt_channel" -> SourceType.YT_CHANNEL
            else -> fail("Unknown expected type: ${fixture.expectedType}")
        }
        assertEquals("Type mismatch for '${fixture.description}'", expectedType, source.type)
        assertEquals("ID mismatch for '${fixture.description}'", fixture.expectedId, source.id)
    }

    private fun assertRejected(fixture: TestUrl) {
        val result = ContentSourceParser.parse(fixture.input)
        assertTrue(
            "Expected Rejected for '${fixture.description}' (${fixture.input}), got: $result",
            result is ParseResult.Rejected,
        )
        if (fixture.expectedMessage != null && fixture.expectedMessage != "yt_video_fallback") {
            val msg = (result as ParseResult.Rejected).message
            assertTrue(
                "Rejection message should contain '${fixture.expectedMessage}', got: '$msg'",
                msg.contains(fixture.expectedMessage, ignoreCase = true),
            )
        }
    }

    @Test
    fun `playlist URLs parse correctly`() {
        PLAYLIST_URLS.forEach { assertSuccess(it) }
    }

    @Test
    fun `video URLs parse correctly`() {
        VIDEO_URLS.forEach { assertSuccess(it) }
    }

    @Test
    fun `channel URLs parse correctly`() {
        CHANNEL_URLS.forEach { assertSuccess(it) }
    }

    @Test
    fun `vimeo URLs rejected with message`() {
        VIMEO_URLS.forEach { assertRejected(it) }
    }

    @Test
    fun `direct file URLs rejected`() {
        DIRECT_FILE_URLS.forEach { assertRejected(it) }
    }

    @Test
    fun `private IPs blocked`() {
        PRIVATE_IP_URLS.forEach { assertRejected(it) }
    }

    @Test
    fun `non-video URLs rejected`() {
        NON_VIDEO_URLS.forEach { assertRejected(it) }
    }

    @Test
    fun `show URL edge cases`() {
        // First one: show without PL after VL → rejected
        assertRejected(SHOW_EDGE_CASES[0])
        // Second one: empty show path → rejected
        assertRejected(SHOW_EDGE_CASES[1])
    }

    @Test
    fun `tricky edge cases handled`() {
        // 62: list param without PL prefix but has video ID → should parse as video
        val result62 = ContentSourceParser.parse(TRICKY_EDGE_CASES[0].input)
        // This URL has v=a and list=notPL — the video ID "a" is valid
        assertTrue("Should be Success (video fallback)", result62 is ParseResult.Success)
        val source62 = (result62 as ParseResult.Success).source
        assertEquals(SourceType.YT_VIDEO, source62.type)
        assertEquals("a", source62.id)

        // 63-64: no scheme watch URLs
        assertSuccess(TRICKY_EDGE_CASES[1]) // youtube.com/watch?v=noScheme
        assertSuccess(TRICKY_EDGE_CASES[2]) // www.youtube.com/watch?v=wwwNoScheme

        // 65: empty video ID
        assertRejected(TRICKY_EDGE_CASES[3])

        // 66: empty short URL
        assertRejected(TRICKY_EDGE_CASES[4])

        // 67: empty channel ID
        assertRejected(TRICKY_EDGE_CASES[5])

        // 68: empty handle (@)
        assertRejected(TRICKY_EDGE_CASES[6])

        // 69: empty playlist ID
        assertRejected(TRICKY_EDGE_CASES[7])

        // 70: mix/radio playlist (RD prefix)
        assertRejected(TRICKY_EDGE_CASES[8])

        // 71: uploads playlist (UU prefix)
        assertRejected(TRICKY_EDGE_CASES[9])

        // 72: liked videos (LL)
        assertRejected(TRICKY_EDGE_CASES[10])

        // 73: watch later (WL)
        assertRejected(TRICKY_EDGE_CASES[11])
    }
}
