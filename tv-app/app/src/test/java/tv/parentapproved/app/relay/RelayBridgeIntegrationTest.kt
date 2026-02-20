package tv.parentapproved.app.relay

import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.auth.PinManager
import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.ChannelDao
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.server.ParentApprovedServer
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Integration test: sends relay-format JSON through FakeWebSocket → RelayConnector → real Ktor server.
 * This is the test that would have caught Bug 2 (path mismatch /api/auth → /auth).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelayBridgeIntegrationTest {

    companion object {
        private lateinit var server: ApplicationEngine
        private var serverPort: Int = 0
        private val sessionManager = SessionManager()
        private val pinManager = PinManager(
            onPinValidated = { sessionManager.createSession() ?: "" },
        )

        @JvmStatic
        @BeforeClass
        fun startServer() {
            val mockDb = mockk<CacheDatabase>()
            val mockChannelDao = mockk<ChannelDao>()
            coEvery { mockChannelDao.count() } returns 2
            coEvery { mockChannelDao.getAll() } returns emptyList()
            every { mockDb.channelDao() } returns mockChannelDao

            val mockPlayEventDao = mockk<tv.parentapproved.app.data.events.PlayEventDao>(relaxed = true)
            every { mockDb.playEventDao() } returns mockPlayEventDao

            ServiceLocator.initForTest(
                db = mockDb,
                pin = pinManager,
                session = sessionManager,
            )
            PlayEventRecorder.init(mockDb, clock = { System.currentTimeMillis() })

            server = embeddedServer(Netty, port = 0) {
                ParentApprovedServer.configureServer(this)
            }.start(wait = false)

            serverPort = runBlocking { server.resolvedConnectors().first().port }
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop(500, 1000)
        }
    }

    private lateinit var fakeWsFactory: FakeWebSocketFactory
    private lateinit var config: RelayConfig
    private var currentTime = 1000000L

    @Before
    fun setup() {
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { prefs.getString("relay_tv_id", null) } returns "test-tv-id"
        every { prefs.getString("relay_tv_secret", null) } returns "test-secret"
        config = RelayConfig(prefs)
        fakeWsFactory = FakeWebSocketFactory()
    }

    private fun createConnector(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        RelayConnector(
            config = config,
            localServerPort = serverPort,
            clock = { currentTime },
            webSocketFactory = fakeWsFactory,
            dispatcher = dispatcher,
        )

    /**
     * Helper: connect the relay, send a request JSON, wait for the response.
     * Returns the last sent message (the response JSON).
     */
    private fun TestScope.sendRelayRequest(requestJson: String): String {
        val connector = createConnector(StandardTestDispatcher(testScheduler))
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk<Response>(relaxed = true))

        val ws = fakeWsFactory.lastWebSocket!!
        fakeWsFactory.lastListener?.onMessage(ws, requestJson)

        // Advance time for the HTTP call to complete (real network to localhost)
        advanceTimeBy(5000)
        runCurrent()

        connector.disconnect()

        // Messages: [0]=connect, [1+]=responses
        assertTrue("Expected at least 2 messages (connect + response), got ${ws.sentMessages.size}",
            ws.sentMessages.size >= 2)
        return ws.sentMessages.last()
    }

    private fun parseResponseStatus(json: String): Int {
        val obj = Json.parseToJsonElement(json).jsonObject
        return obj["status"]!!.jsonPrimitive.int
    }

    private fun parseResponseBody(json: String): JsonObject? {
        val obj = Json.parseToJsonElement(json).jsonObject
        val bodyStr = obj["body"]?.jsonPrimitive?.content ?: return null
        return Json.parseToJsonElement(bodyStr).jsonObject
    }

    // --- Tests ---

    @Test
    fun bridge_getStatus_returns200() = runTest {
        val requestJson = """{"id":"req-status","method":"GET","path":"/api/status","headers":{},"body":null}"""
        val response = sendRelayRequest(requestJson)

        assertEquals(200, parseResponseStatus(response))
        val body = parseResponseBody(response)
        assertNotNull(body)
        assertTrue("Response should contain serverRunning", body!!.containsKey("serverRunning"))
    }

    @Test
    fun bridge_postAuth_withCorrectPin_returns200() = runTest {
        val pin = pinManager.getCurrentPin()
        val requestJson = """{"id":"req-auth","method":"POST","path":"/api/auth","headers":{"Content-Type":"application/json"},"body":"{\"pin\":\"$pin\"}"}"""
        val response = sendRelayRequest(requestJson)

        assertEquals(200, parseResponseStatus(response))
        val body = parseResponseBody(response)
        assertNotNull(body)
        assertTrue("Response should contain token", body!!.containsKey("token"))
    }

    @Test
    fun bridge_postAuth_withWrongPin_returns401() = runTest {
        val requestJson = """{"id":"req-auth-bad","method":"POST","path":"/api/auth","headers":{"Content-Type":"application/json"},"body":"{\"pin\":\"000000\"}"}"""
        val response = sendRelayRequest(requestJson)

        assertEquals(401, parseResponseStatus(response))
    }

    @Test
    fun bridge_getPlaylists_withAuth_returns200() = runTest {
        // First get a valid token
        val pin = pinManager.getCurrentPin()
        val authJson = """{"id":"req-auth2","method":"POST","path":"/api/auth","headers":{"Content-Type":"application/json"},"body":"{\"pin\":\"$pin\"}"}"""
        val authResponse = sendRelayRequest(authJson)
        val token = parseResponseBody(authResponse)!!["token"]!!.jsonPrimitive.content

        // Now request playlists with auth
        val requestJson = """{"id":"req-playlists","method":"GET","path":"/api/playlists","headers":{"Authorization":"Bearer $token"},"body":null}"""
        val response = sendRelayRequest(requestJson)

        assertEquals(200, parseResponseStatus(response))
    }

    @Test
    fun bridge_postPlaybackPause_returns200() = runTest {
        // Get auth token first
        val pin = pinManager.getCurrentPin()
        val authJson = """{"id":"req-auth3","method":"POST","path":"/api/auth","headers":{"Content-Type":"application/json"},"body":"{\"pin\":\"$pin\"}"}"""
        val authResponse = sendRelayRequest(authJson)
        val token = parseResponseBody(authResponse)!!["token"]!!.jsonPrimitive.content

        val requestJson = """{"id":"req-pause","method":"POST","path":"/api/playback/pause","headers":{"Authorization":"Bearer $token"},"body":null}"""
        val response = sendRelayRequest(requestJson)

        assertEquals(200, parseResponseStatus(response))
    }

    @Test
    fun bridge_postAuthRefresh_withValidToken_returns200() = runTest {
        // Get a session token first
        val pin = pinManager.getCurrentPin()
        val authJson = """{"id":"req-auth4","method":"POST","path":"/api/auth","headers":{"Content-Type":"application/json"},"body":"{\"pin\":\"$pin\"}"}"""
        val authResponse = sendRelayRequest(authJson)
        val token = parseResponseBody(authResponse)!!["token"]!!.jsonPrimitive.content

        // Now refresh
        val requestJson = """{"id":"req-refresh","method":"POST","path":"/api/auth/refresh","headers":{"Authorization":"Bearer $token"},"body":null}"""
        val response = sendRelayRequest(requestJson)

        assertEquals(200, parseResponseStatus(response))
        val body = parseResponseBody(response)
        assertNotNull(body)
        assertTrue("Response should contain token", body!!.containsKey("token"))
    }
}
