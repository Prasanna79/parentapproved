package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.PlaylistEntity
import tv.parentapproved.app.util.PlaylistUrlParser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AddPlaylistRequest(val url: String? = null)

@Serializable
data class PlaylistResponse(
    val id: Long,
    val youtubePlaylistId: String,
    val displayName: String,
    val addedAt: Long,
    val status: String,
)

private const val MAX_PLAYLISTS = 10

fun Route.playlistRoutes(sessionManager: SessionManager, database: CacheDatabase) {
    get("/playlists") {
        if (!validateSession(sessionManager)) return@get
        val playlists = database.playlistDao().getAll()
        call.respond(playlists.map { it.toResponse() })
    }

    post("/playlists") {
        if (!validateSession(sessionManager)) return@post

        val body = try {
            call.receive<AddPlaylistRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            return@post
        }

        val url = body.url
        if (url.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "URL is required"))
            return@post
        }

        val playlistId = PlaylistUrlParser.parse(url)
        if (playlistId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Not a valid YouTube playlist URL"))
            return@post
        }

        val dao = database.playlistDao()

        // Check duplicate
        if (dao.getByYoutubeId(playlistId) != null) {
            call.respond(HttpStatusCode.Conflict, mapOf("error" to "Playlist already exists"))
            return@post
        }

        // Check max
        if (dao.count() >= MAX_PLAYLISTS) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Maximum of $MAX_PLAYLISTS playlists reached"))
            return@post
        }

        val entity = PlaylistEntity(
            youtubePlaylistId = playlistId,
            displayName = playlistId,
        )
        val id = dao.insert(entity)
        val saved = entity.copy(id = id)
        call.respond(HttpStatusCode.Created, saved.toResponse())
    }

    delete("/playlists/{id}") {
        if (!validateSession(sessionManager)) return@delete

        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid playlist ID"))
            return@delete
        }

        val dao = database.playlistDao()
        val existing = dao.getAll().find { it.id == id }
        if (existing == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Playlist not found"))
            return@delete
        }

        dao.deleteById(id)
        database.videoDao().deleteByPlaylist(existing.youtubePlaylistId)
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }
}

private fun PlaylistEntity.toResponse() = PlaylistResponse(
    id = id,
    youtubePlaylistId = youtubePlaylistId,
    displayName = displayName,
    addedAt = addedAt,
    status = status,
)
