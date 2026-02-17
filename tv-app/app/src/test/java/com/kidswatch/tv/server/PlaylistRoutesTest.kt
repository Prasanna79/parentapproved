package com.kidswatch.tv.server

import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.data.FakePlaylistDao
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.cache.PlaylistDao
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PlaylistRoutesTest {

    private var currentTime = 1000000L

    private fun testApp(
        setupDao: FakePlaylistDao.() -> Unit = {},
        block: suspend ApplicationTestBuilder.(token: String) -> Unit,
    ) = testApplication {
        val sessionManager = SessionManager(clock = { currentTime })
        val fakeDao = FakePlaylistDao()
        fakeDao.setupDao()

        val mockDb = mockk<CacheDatabase>()
        every { mockDb.playlistDao() } returns fakeDao
        val mockVideoDao = mockk<com.kidswatch.tv.data.cache.PlaylistCacheDao>()
        every { mockDb.videoDao() } returns mockVideoDao
        coEvery { mockVideoDao.deleteByPlaylist(any()) } returns Unit

        application {
            install(ContentNegotiation) { json() }
            routing {
                playlistRoutes(sessionManager, mockDb)
            }
        }

        val token = sessionManager.createSession()!!
        block(token)
    }

    @Test
    fun getPlaylists_empty_returnsEmptyArray() = testApp { token ->
        val response = client.get("/playlists") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(0, body.size)
    }

    @Test
    fun postPlaylist_validUrl_returns201() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://www.youtube.com/playlist?list=PLtest123"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("PLtest123", body["youtubePlaylistId"]?.jsonPrimitive?.content)
    }

    @Test
    fun postPlaylist_invalidUrl_returns400() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://vimeo.com/12345"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postPlaylist_nonPlaylistUrl_returns400() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postPlaylist_duplicateUrl_returns409() = testApp(
        setupDao = {
            // Pre-populate with a playlist
            kotlinx.coroutines.runBlocking {
                insert(com.kidswatch.tv.data.cache.PlaylistEntity(youtubePlaylistId = "PLexisting", displayName = "Existing"))
            }
        }
    ) { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://www.youtube.com/playlist?list=PLexisting"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun postPlaylist_at10Max_returns400() = testApp(
        setupDao = {
            kotlinx.coroutines.runBlocking {
                repeat(10) { i ->
                    insert(com.kidswatch.tv.data.cache.PlaylistEntity(youtubePlaylistId = "PL$i", displayName = "P$i"))
                }
            }
        }
    ) { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"PLnew123456"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun deletePlaylist_existingId_returns200() = testApp(
        setupDao = {
            kotlinx.coroutines.runBlocking {
                insert(com.kidswatch.tv.data.cache.PlaylistEntity(youtubePlaylistId = "PLdel", displayName = "Delete Me"))
            }
        }
    ) { token ->
        // Get the ID
        val listResponse = client.get("/playlists") {
            header("Authorization", "Bearer $token")
        }
        val playlists = Json.parseToJsonElement(listResponse.bodyAsText()).jsonArray
        val id = playlists[0].jsonObject["id"]?.jsonPrimitive?.content

        val response = client.delete("/playlists/$id") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun deletePlaylist_nonExistentId_returns404() = testApp { token ->
        val response = client.delete("/playlists/999") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
