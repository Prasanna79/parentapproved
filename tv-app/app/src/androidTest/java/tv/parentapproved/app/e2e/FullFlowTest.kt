package tv.parentapproved.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.auth.PinManager
import tv.parentapproved.app.auth.PinResult
import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.ChannelEntity
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
    fun e2e_authThenAddSource_fullCycle() = runBlocking {
        // Authenticate
        val pin = ServiceLocator.pinManager.getCurrentPin()
        val result = ServiceLocator.pinManager.validate(pin)
        assertTrue(result is PinResult.Success)

        // Create session
        val token = ServiceLocator.sessionManager.createSession()
        assertNotNull(token)
        assertTrue(ServiceLocator.sessionManager.validateSession(token!!))

        // Add source
        val entity = ChannelEntity(sourceType = "yt_playlist", sourceId = "PLtest123", sourceUrl = "https://www.youtube.com/playlist?list=PLtest123", displayName = "Test Playlist")
        val id = db.channelDao().insert(entity)
        assertTrue(id > 0)

        // List sources
        val channels = db.channelDao().getAll()
        assertEquals(1, channels.size)
        assertEquals("PLtest123", channels[0].sourceId)

        // Delete source
        db.channelDao().deleteById(id)
        assertEquals(0, db.channelDao().count())
    }

    @Test
    fun e2e_addSourceViaDao_verifyInDb() = runBlocking {
        db.channelDao().insert(ChannelEntity(sourceType = "yt_playlist", sourceId = "PLabc", sourceUrl = "url1", displayName = "ABC"))
        db.channelDao().insert(ChannelEntity(sourceType = "yt_video", sourceId = "vid1", sourceUrl = "url2", displayName = "DEF"))

        val all = db.channelDao().getAll()
        assertEquals(2, all.size)

        val found = db.channelDao().getBySourceId("PLabc")
        assertNotNull(found)
        assertEquals("ABC", found!!.displayName)
    }

    @Test
    fun e2e_fullReset_clearsEverything() = runBlocking {
        // Add some data
        db.channelDao().insert(ChannelEntity(sourceType = "yt_playlist", sourceId = "PL1", sourceUrl = "url1", displayName = "P1"))
        db.channelDao().insert(ChannelEntity(sourceType = "yt_playlist", sourceId = "PL2", sourceUrl = "url2", displayName = "P2"))
        tv.parentapproved.app.data.events.PlayEventRecorder.init(db)
        db.playEventDao().insert(tv.parentapproved.app.data.events.PlayEventEntity(
            videoId = "v1", playlistId = "PL1", startedAt = System.currentTimeMillis()
        ))

        // Full reset
        db.channelDao().deleteAll()
        db.playEventDao().deleteAll()
        ServiceLocator.pinManager.resetPin()
        ServiceLocator.sessionManager.invalidateAll()

        assertEquals(0, db.channelDao().count())
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
