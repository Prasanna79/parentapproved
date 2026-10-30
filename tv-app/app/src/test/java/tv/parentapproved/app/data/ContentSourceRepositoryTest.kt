package tv.parentapproved.app.data

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeChannelExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeChannelTabExtractor
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.PlaylistCacheDao
import tv.parentapproved.app.data.cache.VideoEntity
import tv.parentapproved.app.util.AppLogger
import java.io.IOException
import org.junit.Test

/**
 * Tests for ContentSourceRepository pure functions.
 * Extraction failures use mocked extractors; no requests reach YouTube.
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

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun channelTabFailure_preservesCachedVideos() = runBlocking {
        val (db, videoDao) = failingChannelDatabase()
        coEvery { videoDao.getByPlaylist("@CoolChannel") } returns listOf(
            VideoEntity("video1", "@CoolChannel", "Cached video", "", 60L, 0),
        )

        val result = ContentSourceRepository.resolveAllChannels(listOf(channelMeta()), db)
            .getValue("@CoolChannel")

        assertTrue(result is SourceResult.CachedFallback)
        assertEquals("video1", (result as SourceResult.CachedFallback).videos.single().videoId)
        coVerify(exactly = 0) { videoDao.deleteByPlaylist(any()) }
        coVerify(exactly = 0) { videoDao.insertAll(any()) }
    }

    @Test
    fun channelTabFailure_withoutCache_reportsError() = runBlocking {
        val (db, videoDao) = failingChannelDatabase()
        coEvery { videoDao.getByPlaylist("@CoolChannel") } returns emptyList()

        val result = ContentSourceRepository.resolveAllChannels(listOf(channelMeta()), db)
            .getValue("@CoolChannel")

        assertTrue(result is SourceResult.Error)
        assertEquals("Channel tab unavailable", (result as SourceResult.Error).message)
        coVerify(exactly = 0) { videoDao.deleteByPlaylist(any()) }
    }

    private fun channelMeta() = ChannelMeta(
        1L, "yt_channel", "@CoolChannel", "https://www.youtube.com/@CoolChannel", "Cool Channel",
    )

    private fun failingChannelDatabase(): Pair<CacheDatabase, PlaylistCacheDao> {
        mockkObject(AppLogger)
        every { AppLogger.log(any(), any()) } just Runs
        mockkStatic(NewPipe::class)
        every { NewPipe.getDownloader() } returns mockk()
        mockkConstructor(YoutubeChannelExtractor::class, YoutubeChannelTabExtractor::class)
        every { anyConstructed<YoutubeChannelExtractor>().fetchPage() } just Runs
        every { anyConstructed<YoutubeChannelExtractor>().name } returns "Cool Channel"
        val url = "https://www.youtube.com/@CoolChannel/videos"
        every { anyConstructed<YoutubeChannelExtractor>().tabs } returns listOf(
            ListLinkHandler(url, url, "@CoolChannel", listOf("videos"), ""),
        )
        every { anyConstructed<YoutubeChannelTabExtractor>().fetchPage() } just Runs
        every { anyConstructed<YoutubeChannelTabExtractor>().initialPage } throws
            IOException("Channel tab unavailable")
        val db = mockk<CacheDatabase>()
        val videoDao = mockk<PlaylistCacheDao>(relaxed = true)
        every { db.videoDao() } returns videoDao
        return db to videoDao
    }

}
