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

class AuthRoutesTest {

    // Shared mutable time reference
    private class TimeRef(var value: Long = 1000000L)

    private fun testApp(
        timeRef: TimeRef = TimeRef(),
        block: suspend ApplicationTestBuilder.(pin: String, timeRef: TimeRef) -> Unit
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

        block(pin, timeRef)
    }

    @Test
    fun postAuth_correctPin_returns200WithCookie() = testApp { pin, _ ->
        val response = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("true", body["success"]?.jsonPrimitive?.content)
        assertNotNull(body["token"])
    }

    @Test
    fun postAuth_wrongPin_returns401() = testApp { _, _ ->
        val response = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postAuth_missingPin_returns400() = testApp { _, _ ->
        val response = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postAuth_rateLimited_returns429() = testApp { _, _ ->
        repeat(5) {
            client.post("/auth") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"wrong!"}""")
            }
        }
        val response = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"wrong!"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun postAuth_afterLockoutExpires_allowsRetry() {
        val timeRef = TimeRef()
        testApp(timeRef) { _, tr ->
            repeat(5) {
                client.post("/auth") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"pin":"wrong!"}""")
                }
            }
            // Advance time past lockout
            tr.value += 6 * 60 * 1000L

            val response = client.post("/auth") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"wrong!"}""")
            }
            // Should be 401 (Invalid) not 429 (RateLimited)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
