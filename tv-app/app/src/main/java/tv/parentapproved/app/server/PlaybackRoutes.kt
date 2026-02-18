package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.playback.PlaybackCommand
import tv.parentapproved.app.playback.PlaybackCommandBus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.playbackRoutes(sessionManager: SessionManager) {
    post("/playback/stop") {
        if (!validateSession(sessionManager)) return@post
        PlaybackCommandBus.send(PlaybackCommand.Stop)
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    post("/playback/skip") {
        if (!validateSession(sessionManager)) return@post
        PlaybackCommandBus.send(PlaybackCommand.SkipNext)
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }

    post("/playback/pause") {
        if (!validateSession(sessionManager)) return@post
        PlaybackCommandBus.send(PlaybackCommand.TogglePause)
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}
