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

/**
 * End-to-end auth integration tests.
 * Tests the full flow: PIN auth → token → refresh → use refreshed token → protected routes.
 */
class AuthIntegrationTest {

    private class TimeRef(var value: Long = 1000000L)

    private fun testApp(
        timeRef: TimeRef = TimeRef(),
        block: suspend ApplicationTestBuilder.(pin: String, sessionManager: SessionManager, timeRef: TimeRef) -> Unit
    ) = testApplication {
        val sessionManager = SessionManager(clock = { timeRef.value })
        val pinManager = PinManager(
            clock = { timeRef.value },
            onPinValidated = { sessionManager.createSession() ?: "" }
        )
        val pin = pinManager.getCurrentPin()

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pinManager, sessionManager)
            }
        }

        block(pin, sessionManager, timeRef)
    }

    @Test
    fun fullFlow_auth_then_refresh_then_useNewToken() = testApp { pin, sm, _ ->
        // Step 1: Auth with PIN
        val authResponse = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }
        assertEquals(HttpStatusCode.OK, authResponse.status)
        val authBody = Json.parseToJsonElement(authResponse.bodyAsText()).jsonObject
        val originalToken = authBody["token"]!!.jsonPrimitive.content

        // Step 2: Refresh token
        val refreshResponse = client.post("/auth/refresh") {
            header("Authorization", "Bearer $originalToken")
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val refreshBody = Json.parseToJsonElement(refreshResponse.bodyAsText()).jsonObject
        val refreshedToken = refreshBody["token"]!!.jsonPrimitive.content
        assertNotEquals(originalToken, refreshedToken)

        // Step 3: Old token is invalid
        assertFalse(sm.validateSession(originalToken))

        // Step 4: New token is valid
        assertTrue(sm.validateSession(refreshedToken))
    }

    @Test
    fun fullFlow_auth_then_refresh_twice() = testApp { pin, sm, _ ->
        // Auth
        val authResponse = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }
        val token1 = Json.parseToJsonElement(authResponse.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        // First refresh
        val refresh1 = client.post("/auth/refresh") {
            header("Authorization", "Bearer $token1")
        }
        val token2 = Json.parseToJsonElement(refresh1.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        // Second refresh with the refreshed token
        val refresh2 = client.post("/auth/refresh") {
            header("Authorization", "Bearer $token2")
        }
        assertEquals(HttpStatusCode.OK, refresh2.status)
        val token3 = Json.parseToJsonElement(refresh2.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        // Only the latest token is valid
        assertFalse(sm.validateSession(token1))
        assertFalse(sm.validateSession(token2))
        assertTrue(sm.validateSession(token3))
    }

    @Test
    fun refresh_withOldToken_afterRefresh_returns401() = testApp { pin, _, _ ->
        // Auth
        val authResponse = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }
        val originalToken = Json.parseToJsonElement(authResponse.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        // Refresh once
        client.post("/auth/refresh") {
            header("Authorization", "Bearer $originalToken")
        }

        // Try to refresh again with the old (now invalid) token
        val failedRefresh = client.post("/auth/refresh") {
            header("Authorization", "Bearer $originalToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, failedRefresh.status)
    }

    @Test
    fun auth_wrongPin_then_correctPin_works() = testApp { pin, _, _ ->
        // Wrong PIN first
        val wrongResponse = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongResponse.status)

        // Correct PIN works
        val correctResponse = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }
        assertEquals(HttpStatusCode.OK, correctResponse.status)
    }

    @Test
    fun multipleSessions_refreshOne_othersStillValid() = testApp { pin, sm, _ ->
        // Create two sessions
        val auth1 = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }
        val token1 = Json.parseToJsonElement(auth1.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        val auth2 = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }
        val token2 = Json.parseToJsonElement(auth2.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content

        // Refresh token1
        client.post("/auth/refresh") {
            header("Authorization", "Bearer $token1")
        }

        // token2 should still be valid
        assertTrue(sm.validateSession(token2))
    }

    @Test
    fun session_expiresAfter90Days() {
        val timeRef = TimeRef()
        testApp(timeRef) { pin, sm, tr ->
            val authResponse = client.post("/auth") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"$pin"}""")
            }
            val token = Json.parseToJsonElement(authResponse.bodyAsText())
                .jsonObject["token"]!!.jsonPrimitive.content

            assertTrue(sm.validateSession(token))

            // Advance 89 days — still valid
            tr.value += 89L * 24 * 60 * 60 * 1000
            assertTrue(sm.validateSession(token))

            // Advance past 90 days — expired
            tr.value += 2L * 24 * 60 * 60 * 1000
            assertFalse(sm.validateSession(token))
        }
    }

    @Test
    fun refresh_resetsExpiry() {
        val timeRef = TimeRef()
        testApp(timeRef) { pin, sm, tr ->
            // Auth
            val authResponse = client.post("/auth") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"$pin"}""")
            }
            val token = Json.parseToJsonElement(authResponse.bodyAsText())
                .jsonObject["token"]!!.jsonPrimitive.content

            // Advance 80 days
            tr.value += 80L * 24 * 60 * 60 * 1000

            // Refresh — this resets the TTL
            val refreshResponse = client.post("/auth/refresh") {
                header("Authorization", "Bearer $token")
            }
            val newToken = Json.parseToJsonElement(refreshResponse.bodyAsText())
                .jsonObject["token"]!!.jsonPrimitive.content

            // Advance another 80 days (160 total from original auth)
            tr.value += 80L * 24 * 60 * 60 * 1000

            // New token should still be valid (only 80 days since refresh)
            assertTrue(sm.validateSession(newToken))

            // Old token should be invalid
            assertFalse(sm.validateSession(token))
        }
    }
}
