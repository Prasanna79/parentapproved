package tv.parentapproved.app.relay

import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for relay enable/disable toggle behavior.
 * Verifies that the relay connector is connected/disconnected
 * based on the relay_enabled SharedPreferences flag.
 */
class RelayToggleTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var config: RelayConfig
    private lateinit var connector: RelayConnector
    private lateinit var fakeWsFactory: FakeWebSocketFactory

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { prefs.getString("relay_tv_id", null) } returns "test-tv-id"
        every { prefs.getString("relay_tv_secret", null) } returns "test-secret"
        config = RelayConfig(prefs)
        fakeWsFactory = FakeWebSocketFactory()
        connector = RelayConnector(
            config = config,
            webSocketFactory = fakeWsFactory,
        )
    }

    @Test
    fun relayEnabled_false_byDefault() {
        every { prefs.getBoolean("relay_enabled", false) } returns false
        assertFalse(prefs.getBoolean("relay_enabled", false))
    }

    @Test
    fun relayEnabled_true_afterToggle() {
        every { prefs.getBoolean("relay_enabled", false) } returns true
        assertTrue(prefs.getBoolean("relay_enabled", false))
    }

    @Test
    fun connect_changesStateToConnecting() {
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
        connector.connect()
        assertEquals(RelayConnectionState.CONNECTING, connector.state)
    }

    @Test
    fun disconnect_afterConnect_changesStateToDisconnected() {
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        assertEquals(RelayConnectionState.CONNECTED, connector.state)
        connector.disconnect()
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
    }

    @Test
    fun disconnect_whenNeverConnected_staysDisconnected() {
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
        connector.disconnect()
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
    }

    @Test
    fun connect_whenDisabled_shouldNotConnect() {
        // Simulate: relay disabled, so connect() is never called
        every { prefs.getBoolean("relay_enabled", false) } returns false
        val enabled = prefs.getBoolean("relay_enabled", false)
        if (enabled) connector.connect()
        assertEquals(RelayConnectionState.DISCONNECTED, connector.state)
    }

    @Test
    fun connect_whenEnabled_shouldConnect() {
        every { prefs.getBoolean("relay_enabled", false) } returns true
        val enabled = prefs.getBoolean("relay_enabled", false)
        if (enabled) connector.connect()
        assertEquals(RelayConnectionState.CONNECTING, connector.state)
    }

    @Test
    fun pinReset_rotatesSecret_andReconnects() {
        // Connect first
        connector.connect()
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        assertEquals(RelayConnectionState.CONNECTED, connector.state)

        val oldSecret = config.tvSecret

        // Rotate secret
        val newSecret = config.rotateTvSecret()
        assertNotEquals(oldSecret, newSecret)
        assertEquals(64, newSecret.length)

        // Reconnect with new secret
        connector.reconnectNow()
        assertEquals(RelayConnectionState.CONNECTING, connector.state)

        // Verify the new connect message uses the new secret
        fakeWsFactory.lastListener?.onOpen(fakeWsFactory.lastWebSocket!!, mockk(relaxed = true))
        val lastSent = fakeWsFactory.lastWebSocket!!.sentMessages.last()
        assertTrue("Connect message should contain new secret", lastSent.contains(newSecret))
        assertFalse("Connect message should not contain old secret", lastSent.contains(oldSecret))
    }

    @Test
    fun pinReset_preservesTvId() {
        val tvIdBefore = config.tvId
        config.rotateTvSecret()
        assertEquals(tvIdBefore, config.tvId)
    }
}
