package com.kidswatch.tv.relay

import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RelayConnectorTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var config: RelayConfig
    private lateinit var fakeWsFactory: FakeWebSocketFactory
    private var currentTime = 1000000L

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { prefs.getString("relay_tv_id", null) } returns "test-tv-id"
        every { prefs.getString("relay_tv_secret", null) } returns "test-secret"
        config = RelayConfig(prefs)
        fakeWsFactory = FakeWebSocketFactory()
    }

    private fun createConnector(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = StandardTestDispatcher()
    ) = RelayConnector(
        config = config,
        clock = { currentTime },
        webSocketFactory = fakeWsFactory,
        dispatcher = dispatcher,
    )

    @Test
    fun connect_changesStateToConnecting() {
        val connector = createConnector()
        connector.connect()
        assertEquals(RelayConnectionState.CONNECTING, connector.state)
    }

    @Test
    fun connect_sendsConnectMessage_onOpen() {
        val connector = createConnector()
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        assertEquals(RelayConnectionState.CONNECTED, connector.state)
        val sent = fakeWsFactory.lastWebSocket!!.sentMessages
        assertTrue(sent.isNotEmpty())
        assertTrue(sent[0].contains("connect"))
        assertTrue(sent[0].contains("test-tv-id"))
    }

    @Test
    fun connect_usesCorrectWsUrl() {
        val connector = createConnector()
        connector.connect()
        val url = fakeWsFactory.lastRequest?.url?.toString()
        assertNotNull(url)
        assertTrue("URL should contain relay host and TV path, got: $url",
            url!!.contains("relay.parentapproved.tv/tv/test-tv-id/ws"))
    }

    @Test
    fun disconnect_changesStateToDisconnected() {
        val connector = createConnector()
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        connector.disconnect()
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
    }

    @Test
    fun disconnect_doesNotReconnect() = runTest {
        val connector = createConnector(StandardTestDispatcher(testScheduler))
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        connector.disconnect()
        // Advance past any potential reconnect delay; scope is cancelled so nothing should run
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
    }

    @Test
    fun onFailure_schedulesReconnect() {
        val connector = createConnector()
        connector.connect()
        fakeWsFactory.lastListener?.onFailure(
            fakeWsFactory.lastWebSocket!!,
            Exception("network error"),
            null
        )
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
    }

    @Test
    fun backoff_doublesOnEachFailure() {
        val connector = createConnector()
        connector.connect()
        assertEquals(1000, connector.getCurrentBackoffMs())

        // First failure
        fakeWsFactory.lastListener?.onFailure(fakeWsFactory.lastWebSocket!!, Exception(), null)
        assertEquals(2000, connector.getCurrentBackoffMs())
    }

    @Test
    fun backoff_capsAt60s() {
        val connector = createConnector()
        connector.connect()

        // Simulate many failures
        repeat(10) {
            fakeWsFactory.lastListener?.onFailure(fakeWsFactory.lastWebSocket!!, Exception(), null)
        }
        assertTrue(connector.getCurrentBackoffMs() <= 60_000)
    }

    @Test
    fun backoff_resetsOnSuccessfulConnect() {
        val connector = createConnector()
        connector.connect()

        // Fail once (backoff becomes 2000)
        fakeWsFactory.lastListener?.onFailure(fakeWsFactory.lastWebSocket!!, Exception(), null)
        assertEquals(2000, connector.getCurrentBackoffMs())

        // Successful connect on retry
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        assertEquals(1000, connector.getCurrentBackoffMs())
    }

    @Test
    fun onMessage_parsesRelayRequest_andBridgesLocally() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val connector = createConnector(testDispatcher)
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))

        // Capture the websocket before sending message
        val ws = fakeWsFactory.lastWebSocket!!

        // Send a relay request -- this will fail to connect to localhost, generating an error response
        val requestJson = """{"id":"req-001","method":"GET","path":"/api/status","headers":{},"body":null}"""
        fakeWsFactory.lastListener?.onMessage(ws, requestJson)

        // Advance enough time for the request to be dispatched, but not so much that
        // the heartbeat loop runs forever (heartbeat runs every 30s)
        advanceTimeBy(1000)
        runCurrent()

        // Disconnect to stop the heartbeat loop
        connector.disconnect()

        // Should have sent connect message + error response
        val sent = ws.sentMessages
        assertTrue("Expected at least 2 messages (connect + response), got ${sent.size}: $sent",
            sent.size >= 2)
        val lastMsg = sent.last()
        assertTrue(lastMsg.contains("req-001"))
        assertTrue(lastMsg.contains("500") || lastMsg.contains("status"))
    }

    @Test
    fun onMessage_invalidJson_ignored() {
        val connector = createConnector()
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        fakeWsFactory.lastListener?.onMessage(fakeWsFactory.lastWebSocket!!, "not json")
        // Should not crash, just ignore
    }

    @Test
    fun reconnectNow_resetsBackoff() {
        val connector = createConnector()
        connector.connect()

        // Fail to increase backoff
        fakeWsFactory.lastListener?.onFailure(fakeWsFactory.lastWebSocket!!, Exception(), null)
        assertTrue(connector.getCurrentBackoffMs() > 1000)

        connector.reconnectNow()
        assertEquals(1000, connector.getCurrentBackoffMs())
    }

    @Test
    fun connect_whenAlreadyConnected_doesNothing() {
        val connector = createConnector()
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        val firstWs = fakeWsFactory.lastWebSocket

        connector.connect() // should be no-op
        assertSame(firstWs, fakeWsFactory.lastWebSocket)
    }
}

// Test doubles

class FakeWebSocket : WebSocket {
    val sentMessages = mutableListOf<String>()
    var closed = false
    var closeCode = 0
    var closeReason: String? = null

    override fun send(text: String): Boolean {
        sentMessages.add(text)
        return true
    }
    override fun send(bytes: okio.ByteString): Boolean = true
    override fun close(code: Int, reason: String?): Boolean {
        closed = true
        closeCode = code
        closeReason = reason
        return true
    }
    override fun cancel() { closed = true }
    override fun queueSize(): Long = 0
    override fun request(): Request = Request.Builder().url("https://test.com").build()
}

class FakeWebSocketFactory : WebSocketFactory {
    var lastWebSocket: FakeWebSocket? = null
    var lastListener: WebSocketListener? = null
    var lastRequest: Request? = null

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        lastRequest = request
        lastListener = listener
        lastWebSocket = FakeWebSocket()
        return lastWebSocket!!
    }
}
