package tv.parentapproved.app.data

import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.events.PlayEventDao
import tv.parentapproved.app.data.events.PlayEventEntity
import tv.parentapproved.app.data.events.PlayEventRecorder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayEventRecorderTest {

    private lateinit var mockDb: CacheDatabase
    private lateinit var mockDao: PlayEventDao
    private var fakeTime = 10_000L

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        coEvery { mockDao.insert(any()) } returns 1L
        mockDb = mockk()
        every { mockDb.playEventDao() } returns mockDao
        PlayEventRecorder.init(mockDb, clock = { fakeTime })
    }

    @Test
    fun startEvent_setsCurrentVideoTitle() {
        PlayEventRecorder.startEvent("vid1", "pl1", title = "My Video", playlistTitle = "My Playlist", durationMs = 120_000)
        assertEquals("My Video", PlayEventRecorder.currentTitle)
    }

    @Test
    fun startEvent_setsCurrentPlaylistTitle() {
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "Cool Playlist", durationMs = 60_000)
        assertEquals("Cool Playlist", PlayEventRecorder.currentPlaylistTitle)
    }

    @Test
    fun startEvent_setsIsPlayingTrue() {
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 60_000)
        assertTrue(PlayEventRecorder.isPlaying)
    }

    @Test
    fun pauseEvent_setsIsPlayingFalse() {
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 60_000)
        fakeTime = 15_000L
        PlayEventRecorder.onPause()
        assertFalse(PlayEventRecorder.isPlaying)
    }

    @Test
    fun resumeEvent_setsIsPlayingTrue() {
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 60_000)
        fakeTime = 15_000L
        PlayEventRecorder.onPause()
        PlayEventRecorder.onResume()
        assertTrue(PlayEventRecorder.isPlaying)
    }

    @Test
    fun endEvent_clearsAllNowPlayingFields() {
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 60_000)
        PlayEventRecorder.endEvent(10, 50)
        assertNull(PlayEventRecorder.currentVideoId)
        assertNull(PlayEventRecorder.currentPlaylistId)
        assertNull(PlayEventRecorder.currentTitle)
        assertNull(PlayEventRecorder.currentPlaylistTitle)
        assertFalse(PlayEventRecorder.isPlaying)
        assertEquals(0L, PlayEventRecorder.currentDurationMs)
    }

    @Test
    fun updateTitle_changesCurrentTitle() {
        PlayEventRecorder.startEvent("dQw4w9WgXcQ", "pl1", title = "dQw4w9WgXcQ", playlistTitle = "PL", durationMs = 60_000)
        assertEquals("dQw4w9WgXcQ", PlayEventRecorder.currentTitle)
        PlayEventRecorder.updateTitle("Never Gonna Give You Up")
        assertEquals("Never Gonna Give You Up", PlayEventRecorder.currentTitle)
    }

    @Test
    fun updateTitle_doesNotOverwriteExistingGoodTitle() {
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Already Good Title", playlistTitle = "PL", durationMs = 60_000)
        PlayEventRecorder.updateTitle("Different Title")
        // currentTitle is always updated (server needs latest), but DB preserves the original
        assertEquals("Different Title", PlayEventRecorder.currentTitle)
    }

    @Test
    fun getElapsedMs_whilePlaying_returnsWallClockDelta() {
        fakeTime = 10_000L
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 60_000)
        fakeTime = 15_000L
        assertEquals(5_000L, PlayEventRecorder.getElapsedMs())
    }

    @Test
    fun getElapsedMs_whilePaused_returnsFrozenValue() {
        fakeTime = 10_000L
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 60_000)
        fakeTime = 13_000L
        PlayEventRecorder.onPause()
        fakeTime = 20_000L // time passes while paused
        assertEquals(3_000L, PlayEventRecorder.getElapsedMs())
    }
}
