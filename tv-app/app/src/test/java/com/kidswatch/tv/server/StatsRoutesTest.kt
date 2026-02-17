package com.kidswatch.tv.server

import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.data.FakePlayEventDao
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.events.PlayEventEntity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class StatsRoutesTest {

    private fun testApp(
        setupDao: suspend FakePlayEventDao.() -> Unit = {},
        block: suspend ApplicationTestBuilder.(token: String) -> Unit,
    ) = testApplication {
        val sessionManager = SessionManager()
        val fakeDao = FakePlayEventDao()
        runBlocking { fakeDao.setupDao() }

        val mockDb = mockk<CacheDatabase>()
        every { mockDb.playEventDao() } returns fakeDao

        application {
            install(ContentNegotiation) { json() }
            routing {
                statsRoutes(sessionManager, mockDb)
            }
        }

        val token = sessionManager.createSession()!!
        block(token)
    }

    @Test
    fun getStats_noEvents_returnsZeros() = testApp { token ->
        val response = client.get("/stats") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("0", body["totalEventsToday"]?.jsonPrimitive?.content)
        assertEquals("0", body["totalWatchTimeToday"]?.jsonPrimitive?.content)
        assertEquals("0", body["totalEventsAllTime"]?.jsonPrimitive?.content)
    }

    @Test
    fun getStats_withEvents_returnsSummary() = testApp(
        setupDao = {
            val now = System.currentTimeMillis()
            insert(PlayEventEntity(videoId = "v1", playlistId = "PL1", startedAt = now, durationSec = 120))
            insert(PlayEventEntity(videoId = "v2", playlistId = "PL1", startedAt = now - 1000, durationSec = 60))
        }
    ) { token ->
        val response = client.get("/stats") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("2", body["totalEventsAllTime"]?.jsonPrimitive?.content)
    }

    @Test
    fun getStatsRecent_returns20MostRecent() = testApp(
        setupDao = {
            repeat(25) { i ->
                insert(PlayEventEntity(videoId = "v$i", playlistId = "PL1", startedAt = 1000L + i, durationSec = 30))
            }
        }
    ) { token ->
        val response = client.get("/stats/recent") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(20, body.size)
    }
}
