package com.kidswatch.tv.server

import com.kidswatch.tv.auth.PinManager
import com.kidswatch.tv.auth.PinResult
import com.kidswatch.tv.auth.SessionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val pin: String? = null)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val attemptsRemaining: Int? = null,
    val retryAfterMs: Long? = null,
    val error: String? = null,
)

fun Route.authRoutes(pinManager: PinManager, sessionManager: SessionManager) {
    post("/auth") {
        val body = try {
            call.receive<AuthRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, AuthResponse(success = false, error = "Missing or invalid request body"))
            return@post
        }

        val pin = body.pin
        if (pin.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, AuthResponse(success = false, error = "PIN is required"))
            return@post
        }

        when (val result = pinManager.validate(pin)) {
            is PinResult.Success -> {
                val token = sessionManager.createSession()
                call.respond(HttpStatusCode.OK, AuthResponse(success = true, token = token))
            }
            is PinResult.Invalid -> {
                call.respond(HttpStatusCode.Unauthorized, AuthResponse(
                    success = false,
                    attemptsRemaining = result.attemptsRemaining,
                    error = "Invalid PIN"
                ))
            }
            is PinResult.RateLimited -> {
                call.respond(HttpStatusCode.TooManyRequests, AuthResponse(
                    success = false,
                    retryAfterMs = result.retryAfterMs,
                    error = "Too many attempts"
                ))
            }
        }
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.validateSession(sessionManager: SessionManager): Boolean {
    val authHeader = call.request.header("Authorization")
    val token = authHeader?.removePrefix("Bearer ")
        ?: call.request.cookies["session"]
    if (token == null || !sessionManager.validateSession(token)) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
        return false
    }
    return true
}
