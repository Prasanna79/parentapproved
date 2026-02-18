package com.kidswatch.tv.server

import com.kidswatch.tv.auth.SessionManager
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.Assert.*
import org.junit.Test

class AuthMiddlewareTest {

    private var currentTime = 1000000L

    private fun testApp(block: suspend ApplicationTestBuilder.(token: String) -> Unit) = testApplication {
        val sessionManager = SessionManager(clock = { currentTime })

        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/protected") {
                    if (!validateSession(sessionManager)) return@get
                    call.respondText("OK")
                }
                get("/public") {
                    call.respondText("Public")
                }
            }
        }

        val token = sessionManager.createSession() ?: ""
        block(token)
    }

    @Test
    fun protectedRoute_noSession_returns401() = testApp { _ ->
        val response = client.get("/protected")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun protectedRoute_invalidSession_returns401() = testApp { _ ->
        val response = client.get("/protected") {
            header("Authorization", "Bearer invalid_token_here")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun protectedRoute_validSession_returns200() = testApp { token ->
        val response = client.get("/protected") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun protectedRoute_expiredSession_returns401() = testApp { token ->
        // Advance past 90 days
        currentTime += 91L * 24 * 60 * 60 * 1000
        val response = client.get("/protected") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun publicRoutes_noAuthRequired() = testApp { _ ->
        val response = client.get("/public")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
