package com.kidswatch.tv.server

import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.playback.PlaybackCommand
import com.kidswatch.tv.playback.PlaybackCommandBus
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
