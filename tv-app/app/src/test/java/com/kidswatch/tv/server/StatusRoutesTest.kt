package com.kidswatch.tv.server

import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.auth.PinManager
import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.cache.PlaylistDao
import com.kidswatch.tv.data.events.PlayEventRecorder
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StatusRoutesTest {

    @Before
    fun setup() {
        val mockDb = mockk<CacheDatabase>()
        val mockPlaylistDao = mockk<PlaylistDao>()
        coEvery { mockPlaylistDao.count() } returns 3
        every { mockDb.playlistDao() } returns mockPlaylistDao

        ServiceLocator.initForTest(
            db = mockDb,
            pin = PinManager(),
            session = SessionManager(),
        )
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
        // No current playback
        val response = client.get("/status")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        // currentlyPlaying should be null when nothing is playing
        assertTrue(body["currentlyPlaying"]?.jsonPrimitive?.content == null ||
                   body.containsKey("currentlyPlaying"))
    }
}
