package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.FakeChannelDao
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.ChannelEntity
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
        setupDao: FakeChannelDao.() -> Unit = {},
        block: suspend ApplicationTestBuilder.(token: String) -> Unit,
    ) = testApplication {
        val sessionManager = SessionManager(clock = { currentTime })
        val fakeDao = FakeChannelDao()
        fakeDao.setupDao()

        val mockDb = mockk<CacheDatabase>()
        every { mockDb.channelDao() } returns fakeDao
        val mockVideoDao = mockk<tv.parentapproved.app.data.cache.PlaylistCacheDao>()
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
    fun postPlaylist_validPlaylistUrl_returns201() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://www.youtube.com/playlist?list=PLtest123"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("PLtest123", body["sourceId"]?.jsonPrimitive?.content)
        assertEquals("yt_playlist", body["sourceType"]?.jsonPrimitive?.content)
    }

    @Test
    fun postPlaylist_validVideoUrl_returns201() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("dQw4w9WgXcQ", body["sourceId"]?.jsonPrimitive?.content)
        assertEquals("yt_video", body["sourceType"]?.jsonPrimitive?.content)
    }

    @Test
    fun postPlaylist_validChannelUrl_returns201() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://www.youtube.com/@PBSKids"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("@PBSKids", body["sourceId"]?.jsonPrimitive?.content)
        assertEquals("yt_channel", body["sourceType"]?.jsonPrimitive?.content)
    }

    @Test
    fun postPlaylist_vimeoUrl_returns400WithCta() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://vimeo.com/12345"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["error"]!!.jsonPrimitive.content.contains("Vimeo"))
        assertNotNull(body["cta"])
    }

    @Test
    fun postPlaylist_invalidUrl_returns400() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://www.google.com"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postPlaylist_duplicateSource_returns409() = testApp(
        setupDao = {
            kotlinx.coroutines.runBlocking {
                insert(ChannelEntity(sourceType = "yt_playlist", sourceId = "PLexisting", sourceUrl = "url", displayName = "Existing"))
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
    fun postPlaylist_at20Max_returns400() = testApp(
        setupDao = {
            kotlinx.coroutines.runBlocking {
                repeat(20) { i ->
                    insert(ChannelEntity(sourceType = "yt_playlist", sourceId = "PL$i", sourceUrl = "url$i", displayName = "P$i"))
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
                insert(ChannelEntity(sourceType = "yt_playlist", sourceId = "PLdel", sourceUrl = "url", displayName = "Delete Me"))
            }
        }
    ) { token ->
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

    @Test
    fun postPlaylist_barePlaylistId_returns201() = testApp { token ->
        val response = client.post("/playlists") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"url":"PLnew123456"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun getPlaylists_returnsSourceFields() = testApp(
        setupDao = {
            kotlinx.coroutines.runBlocking {
                insert(ChannelEntity(sourceType = "yt_video", sourceId = "vid1", sourceUrl = "https://www.youtube.com/watch?v=vid1", displayName = "My Video", videoCount = 1))
            }
        }
    ) { token ->
        val response = client.get("/playlists") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        val item = body[0].jsonObject
        assertEquals("yt_video", item["sourceType"]?.jsonPrimitive?.content)
        assertEquals("vid1", item["sourceId"]?.jsonPrimitive?.content)
        assertEquals("1", item["videoCount"]?.jsonPrimitive?.content)
    }
}
