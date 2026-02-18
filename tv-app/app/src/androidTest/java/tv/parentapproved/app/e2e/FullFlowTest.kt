package tv.parentapproved.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.auth.PinManager
import tv.parentapproved.app.auth.PinResult
import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.PlaylistEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullFlowTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CacheDatabase

    @Before
    fun setup() {
        db = CacheDatabase.getInMemoryInstance(context)
        ServiceLocator.initForTest(db, PinManager(), SessionManager())
    }

    @Test
    fun e2e_authThenAddPlaylist_fullCycle() = runBlocking {
        // Authenticate
        val pin = ServiceLocator.pinManager.getCurrentPin()
        val result = ServiceLocator.pinManager.validate(pin)
        assertTrue(result is PinResult.Success)

        // Create session
        val token = ServiceLocator.sessionManager.createSession()
        assertNotNull(token)
        assertTrue(ServiceLocator.sessionManager.validateSession(token!!))

        // Add playlist
        val entity = PlaylistEntity(youtubePlaylistId = "PLtest123", displayName = "Test Playlist")
        val id = db.playlistDao().insert(entity)
        assertTrue(id > 0)

        // List playlists
        val playlists = db.playlistDao().getAll()
        assertEquals(1, playlists.size)
        assertEquals("PLtest123", playlists[0].youtubePlaylistId)

        // Delete playlist
        db.playlistDao().deleteById(id)
        assertEquals(0, db.playlistDao().count())
    }

    @Test
    fun e2e_addPlaylistViaDao_verifyInDb() = runBlocking {
        db.playlistDao().insert(PlaylistEntity(youtubePlaylistId = "PLabc", displayName = "ABC"))
        db.playlistDao().insert(PlaylistEntity(youtubePlaylistId = "PLdef", displayName = "DEF"))

        val all = db.playlistDao().getAll()
        assertEquals(2, all.size)

        val found = db.playlistDao().getByYoutubeId("PLabc")
        assertNotNull(found)
        assertEquals("ABC", found!!.displayName)
    }

    @Test
    fun e2e_fullReset_clearsEverything() = runBlocking {
        // Add some data
        db.playlistDao().insert(PlaylistEntity(youtubePlaylistId = "PL1", displayName = "P1"))
        db.playlistDao().insert(PlaylistEntity(youtubePlaylistId = "PL2", displayName = "P2"))
        tv.parentapproved.app.data.events.PlayEventRecorder.init(db)
        db.playEventDao().insert(tv.parentapproved.app.data.events.PlayEventEntity(
            videoId = "v1", playlistId = "PL1", startedAt = System.currentTimeMillis()
        ))

        // Full reset
        db.playlistDao().deleteAll()
        db.playEventDao().deleteAll()
        ServiceLocator.pinManager.resetPin()
        ServiceLocator.sessionManager.invalidateAll()

        assertEquals(0, db.playlistDao().count())
        assertEquals(0, db.playEventDao().count())
        assertEquals(0, ServiceLocator.sessionManager.getActiveSessionCount())
    }

    @Test
    fun e2e_offlineMode_togglesCorrectly() {
        assertFalse(tv.parentapproved.app.util.OfflineSimulator.isOffline)
        tv.parentapproved.app.util.OfflineSimulator.toggle()
        assertTrue(tv.parentapproved.app.util.OfflineSimulator.isOffline)
        tv.parentapproved.app.util.OfflineSimulator.toggle()
        assertFalse(tv.parentapproved.app.util.OfflineSimulator.isOffline)
    }
}
