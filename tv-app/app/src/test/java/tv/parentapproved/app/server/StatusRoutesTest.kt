package tv.parentapproved.app.server

import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.auth.PinManager
import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.ChannelDao
import tv.parentapproved.app.data.events.PlayEventRecorder
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StatusRoutesTest {

    private lateinit var mockDb: CacheDatabase
    private var fakeTime = 10_000L

    @Before
    fun setup() {
        mockDb = mockk<CacheDatabase>()
        val mockChannelDao = mockk<ChannelDao>()
        coEvery { mockChannelDao.count() } returns 3
        every { mockDb.channelDao() } returns mockChannelDao

        val mockPlayEventDao = mockk<tv.parentapproved.app.data.events.PlayEventDao>(relaxed = true)
        coEvery { mockPlayEventDao.insert(any()) } returns 1L
        every { mockDb.playEventDao() } returns mockPlayEventDao

        ServiceLocator.initForTest(
            db = mockDb,
            pin = PinManager(),
            session = SessionManager(),
        )
        PlayEventRecorder.init(mockDb, clock = { fakeTime })
    }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                statusRoutes()
            }
        }
        block()
    }

    @Test
    fun getStatus_returnsCorrectShape() = testApp {
        val response = client.get("/status")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["version"])
        assertNotNull(body["serverRunning"])
        assertNotNull(body["playlistCount"])
        assertNotNull(body["activeSessions"])
    }

    @Test
    fun getStatus_includesNowPlaying() = testApp {
        // No current playback — endEvent to ensure clean state
        PlayEventRecorder.endEvent(0, 0)
        val response = client.get("/status")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        // currentlyPlaying should be null when nothing is playing
        assertTrue(body.containsKey("currentlyPlaying"))
        assertTrue(body["currentlyPlaying"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun getStatus_nowPlaying_includesTitle() = testApp {
        fakeTime = 10_000L
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Cool Video", playlistTitle = "Fun Playlist", durationMs = 120_000)
        val response = client.get("/status")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val np = body["currentlyPlaying"]!!.jsonObject
        assertEquals("Cool Video", np["title"]!!.jsonPrimitive.content)
        assertEquals("Fun Playlist", np["playlistTitle"]!!.jsonPrimitive.content)
        PlayEventRecorder.endEvent(0, 0)
    }

    @Test
    fun getStatus_nowPlaying_titleUpdatedAfterExtraction() = testApp {
        fakeTime = 10_000L
        // Simulate race: startEvent with slug (playlist not loaded yet)
        PlayEventRecorder.startEvent("dQw4w9WgXcQ", "pl1", title = "dQw4w9WgXcQ", playlistTitle = "PL", durationMs = 120_000)
        // Simulate extractor resolving the real title
        PlayEventRecorder.updateTitle("Never Gonna Give You Up")
        val response = client.get("/status")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val np = body["currentlyPlaying"]!!.jsonObject
        assertEquals("Never Gonna Give You Up", np["title"]!!.jsonPrimitive.content)
        PlayEventRecorder.endEvent(0, 0)
    }

    @Test
    fun getStatus_nowPlaying_includesElapsedAndDuration() = testApp {
        fakeTime = 10_000L
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 120_000)
        fakeTime = 15_000L // 5 seconds elapsed
        val response = client.get("/status")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val np = body["currentlyPlaying"]!!.jsonObject
        assertEquals(5, np["elapsedSec"]!!.jsonPrimitive.int)
        assertEquals(120, np["durationSec"]!!.jsonPrimitive.int)
        PlayEventRecorder.endEvent(0, 0)
    }

    @Test
    fun getStatus_nowPlaying_includesPlayingState() = testApp {
        fakeTime = 10_000L
        PlayEventRecorder.startEvent("vid1", "pl1", title = "Vid", playlistTitle = "PL", durationMs = 60_000)
        val response = client.get("/status")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val np = body["currentlyPlaying"]!!.jsonObject
        assertTrue(np["playing"]!!.jsonPrimitive.boolean)
        PlayEventRecorder.endEvent(0, 0)
    }
}
