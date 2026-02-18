package tv.parentapproved.app.server

import tv.parentapproved.app.auth.PinManager
import tv.parentapproved.app.auth.SessionManager
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class AuthRefreshRoutesTest {

    private class TimeRef(var value: Long = 1000000L)

    private fun testApp(
        timeRef: TimeRef = TimeRef(),
        block: suspend ApplicationTestBuilder.(sessionManager: SessionManager, timeRef: TimeRef) -> Unit
    ) = testApplication {
        val sessionManager = SessionManager(clock = { timeRef.value })
        val pinManager = PinManager(
            clock = { timeRef.value },
            onPinValidated = { sessionManager.createSession() ?: "" }
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pinManager, sessionManager)
            }
        }
        block(sessionManager, timeRef)
    }

    @Test
    fun refresh_validToken_returns200WithNewToken() = testApp { sm, _ ->
        val token = sm.createSession()!!
        val response = client.post("/auth/refresh") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("true", body["success"]?.jsonPrimitive?.content)
        assertNotNull(body["token"])
        val newToken = body["token"]!!.jsonPrimitive.content
        assertNotEquals(token, newToken)
    }

    @Test
    fun refresh_invalidToken_returns401() = testApp { _, _ ->
        val response = client.post("/auth/refresh") {
            header("Authorization", "Bearer invalid-token-here")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun refresh_missingToken_returns401() = testApp { _, _ ->
        val response = client.post("/auth/refresh")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun refresh_expiredToken_returns401() {
        val timeRef = TimeRef()
        testApp(timeRef) { sm, tr ->
            val token = sm.createSession()!!
            tr.value += 91L * 24 * 60 * 60 * 1000 // past 90 days
            val response = client.post("/auth/refresh") {
                header("Authorization", "Bearer $token")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun refresh_returnsWorkingToken() = testApp { sm, _ ->
        val token = sm.createSession()!!
        val response = client.post("/auth/refresh") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val newToken = body["token"]!!.jsonPrimitive.content
        // New token should be valid
        assertTrue(sm.validateSession(newToken))
        // Old token should be invalid
        assertFalse(sm.validateSession(token))
    }

    @Test
    fun refresh_preservesOtherSessions() = testApp { sm, _ ->
        val token1 = sm.createSession()!!
        val token2 = sm.createSession()!!
        client.post("/auth/refresh") {
            header("Authorization", "Bearer $token1")
        }
        // token2 should still be valid
        assertTrue(sm.validateSession(token2))
    }
}
