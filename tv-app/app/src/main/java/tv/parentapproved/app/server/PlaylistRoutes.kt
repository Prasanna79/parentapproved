package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.ChannelEntity
import tv.parentapproved.app.util.ContentSourceParser
import tv.parentapproved.app.util.ParseResult
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
    val sourceType: String,
    val sourceId: String,
    val sourceUrl: String,
    val displayName: String,
    val videoCount: Int,
    val addedAt: Long,
    val status: String,
)

private const val MAX_SOURCES = 20

fun Route.playlistRoutes(sessionManager: SessionManager, database: CacheDatabase) {
    get("/playlists") {
        if (!validateSession(sessionManager)) return@get
        val channels = database.channelDao().getAll()
        call.respond(channels.map { it.toResponse() })
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

        val parseResult = ContentSourceParser.parse(url)
        if (parseResult is ParseResult.Rejected) {
            call.respond(HttpStatusCode.BadRequest, mapOf(
                "error" to parseResult.message,
                "cta" to "Try pasting a YouTube video, playlist, or channel URL",
            ))
            return@post
        }

        val source = (parseResult as ParseResult.Success).source
        val dao = database.channelDao()

        // Check duplicate
        if (dao.getBySourceId(source.id) != null) {
            call.respond(HttpStatusCode.Conflict, mapOf("error" to "This source is already added"))
            return@post
        }

        // Check max
        if (dao.count() >= MAX_SOURCES) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Maximum of $MAX_SOURCES sources reached"))
            return@post
        }

        val entity = ChannelEntity(
            sourceType = source.type.name.lowercase(),
            sourceId = source.id,
            sourceUrl = source.canonicalUrl,
            displayName = source.id, // Will be updated on first resolve
        )
        val id = dao.insert(entity)
        val saved = entity.copy(id = id)
        call.respond(HttpStatusCode.Created, saved.toResponse())
    }

    delete("/playlists/{id}") {
        if (!validateSession(sessionManager)) return@delete

        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid source ID"))
            return@delete
        }

        val dao = database.channelDao()
        val existing = dao.getAll().find { it.id == id }
        if (existing == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Source not found"))
            return@delete
        }

        dao.deleteById(id)
        database.videoDao().deleteByPlaylist(existing.sourceId)
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }
}

private fun ChannelEntity.toResponse() = PlaylistResponse(
    id = id,
    sourceType = sourceType,
    sourceId = sourceId,
    sourceUrl = sourceUrl,
    displayName = displayName,
    videoCount = videoCount,
    addedAt = addedAt,
    status = status,
)
