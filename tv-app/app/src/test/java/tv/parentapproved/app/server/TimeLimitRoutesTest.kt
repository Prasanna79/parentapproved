package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.playback.PlaybackCommand
import tv.parentapproved.app.playback.PlaybackCommandBus
import tv.parentapproved.app.timelimits.TimeLimitConfig
import tv.parentapproved.app.timelimits.TimeLimitManager
import tv.parentapproved.app.timelimits.TimeLimitManagerTest.FakeTimeLimitStore
import tv.parentapproved.app.timelimits.TimeLimitManagerTest.FakeWatchTimeProvider
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class TimeLimitRoutesTest {

    private var currentTime = LocalDate.of(2026, 2, 18)
        .atTime(14, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun testApp(
        setupStore: FakeTimeLimitStore.() -> Unit = {},
        setupWatch: FakeWatchTimeProvider.() -> Unit = {},
        block: suspend ApplicationTestBuilder.(token: String) -> Unit,
    ) = testApplication {
        val sessionManager = SessionManager()
        val store = FakeTimeLimitStore()
        store.setupStore()
        val watch = FakeWatchTimeProvider()
        watch.setupWatch()

        val manager = TimeLimitManager(
            clock = { currentTime },
            store = store,
            watchTimeProvider = watch,
        )

        application {
            install(ContentNegotiation) { json() }
            routing {
                timeLimitRoutes(sessionManager, manager)
            }
        }

        val token = sessionManager.createSession()!!
        block(token)
    }

    // --- GET /time-limits ---

    @Test
    fun getTimeLimits_authenticated_returnsConfig() = testApp(
        setupStore = {
            storedConfig = TimeLimitConfig(
                dailyLimits = mapOf(DayOfWeek.MONDAY to 60),
                bedtimeStartMin = 20 * 60 + 30,
                bedtimeEndMin = 7 * 60,
            )
        }
    ) { token ->
        val response = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("allowed", body["currentStatus"]?.jsonPrimitive?.content)
        assertNotNull(body["dailyLimits"])
    }

    @Test
    fun getTimeLimits_noConfigYet_returnsDefaults() = testApp { token ->
        val response = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("allowed", body["currentStatus"]?.jsonPrimitive?.content)
        assertEquals("false", body["manuallyLocked"]?.jsonPrimitive?.content)
    }

    @Test
    fun getTimeLimits_unauthenticated_returns401() = testApp { _ ->
        val response = client.get("/time-limits")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getTimeLimits_includesCurrentStatus() = testApp(
        setupStore = {
            storedConfig = TimeLimitConfig(manuallyLocked = true)
        }
    ) { token ->
        val response = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("blocked", body["currentStatus"]?.jsonPrimitive?.content)
        assertEquals("manual_lock", body["lockReason"]?.jsonPrimitive?.content)
    }

    @Test
    fun getTimeLimits_includesLockReason_whenBlocked() = testApp(
        setupStore = {
            storedConfig = TimeLimitConfig(
                dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
            )
        },
        setupWatch = {
            watchSeconds = 65 * 60
        }
    ) { token ->
        val response = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("blocked", body["currentStatus"]?.jsonPrimitive?.content)
        assertEquals("daily_limit", body["lockReason"]?.jsonPrimitive?.content)
    }

    // --- PUT /time-limits ---

    @Test
    fun putTimeLimits_validConfig_returns200() = testApp { token ->
        val response = client.put("/time-limits") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"dailyLimits":{"monday":60,"tuesday":90}}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun putTimeLimits_updatesPersistedConfig() = testApp { token ->
        client.put("/time-limits") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"dailyLimits":{"monday":60},"bedtimeStartMin":1230,"bedtimeEndMin":420}""")
        }

        val getResponse = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        assertNotNull(body["bedtime"])
    }

    @Test
    fun putTimeLimits_invalidMinutes_returns400() = testApp { token ->
        val response = client.put("/time-limits") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"dailyLimits":{"monday":999}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun putTimeLimits_unauthenticated_returns401() = testApp { _ ->
        val response = client.put("/time-limits") {
            contentType(ContentType.Application.Json)
            setBody("""{"dailyLimits":{"monday":60}}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- POST /time-limits/lock ---

    @Test
    fun postLock_true_locksDevice() = testApp { token ->
        val response = client.post("/time-limits/lock") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"locked":true}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify locked via GET
        val getResponse = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        assertEquals("true", body["manuallyLocked"]?.jsonPrimitive?.content)
    }

    @Test
    fun postLock_false_unlocksDevice() = testApp(
        setupStore = {
            storedConfig = TimeLimitConfig(manuallyLocked = true)
        }
    ) { token ->
        val response = client.post("/time-limits/lock") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"locked":false}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val getResponse = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        assertEquals("false", body["manuallyLocked"]?.jsonPrimitive?.content)
    }

    @Test
    fun postLock_unauthenticated_returns401() = testApp { _ ->
        val response = client.post("/time-limits/lock") {
            contentType(ContentType.Application.Json)
            setBody("""{"locked":true}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postLock_stopsPlayback() = runTest {
        // Collect commands into a list via a launched coroutine
        val received = mutableListOf<PlaybackCommand>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            PlaybackCommandBus.commands.collect { received.add(it) }
        }

        testApplication {
            val sessionManager = SessionManager()
            val store = FakeTimeLimitStore()
            val watch = FakeWatchTimeProvider()
            val manager = TimeLimitManager(clock = { currentTime }, store = store, watchTimeProvider = watch)

            application {
                install(ContentNegotiation) { json() }
                routing { timeLimitRoutes(sessionManager, manager) }
            }

            val token = sessionManager.createSession()!!
            client.post("/time-limits/lock") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"locked":true}""")
            }
        }

        job.cancel()
        assertTrue("Expected Stop command to be received", received.contains(PlaybackCommand.Stop))
    }

    // --- POST /time-limits/bonus ---

    @Test
    fun postBonus_addsMinutes_returns200() = testApp(
        setupStore = {
            storedConfig = TimeLimitConfig(
                dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
            )
        },
        setupWatch = { watchSeconds = 60 * 60 }
    ) { token ->
        val response = client.post("/time-limits/bonus") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"minutes":15}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun postBonus_returnsNewRemaining() = testApp(
        setupStore = {
            storedConfig = TimeLimitConfig(
                dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
            )
        },
        setupWatch = { watchSeconds = 55 * 60 }
    ) { token ->
        val response = client.post("/time-limits/bonus") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"minutes":15}""")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("true", body["success"]?.jsonPrimitive?.content)
        val remaining = body["remainingMin"]?.jsonPrimitive?.content?.toInt()
        assertNotNull(remaining)
        assertTrue("Remaining should be > 0 after bonus, got $remaining", remaining!! > 0)
    }

    @Test
    fun postBonus_zeroMinutes_returns400() = testApp { token ->
        val response = client.post("/time-limits/bonus") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"minutes":0}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postBonus_negativeMinutes_returns400() = testApp { token ->
        val response = client.post("/time-limits/bonus") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"minutes":-5}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postBonus_over240_returns400() = testApp { token ->
        val response = client.post("/time-limits/bonus") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"minutes":300}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun postBonus_unauthenticated_returns401() = testApp { _ ->
        val response = client.post("/time-limits/bonus") {
            contentType(ContentType.Application.Json)
            setBody("""{"minutes":15}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- POST /time-limits/request ---

    @Test
    fun postRequest_noAuth_returns200() = testApp { _ ->
        val response = client.post("/time-limits/request")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun postRequest_rateLimited_returns429() = testApp { _ ->
        client.post("/time-limits/request")
        val response = client.post("/time-limits/request")
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun postRequest_setsHasTimeRequestFlag() = testApp { token ->
        client.post("/time-limits/request")

        val response = client.get("/time-limits") {
            header("Authorization", "Bearer $token")
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("true", body["hasTimeRequest"]?.jsonPrimitive?.content)
    }
}
