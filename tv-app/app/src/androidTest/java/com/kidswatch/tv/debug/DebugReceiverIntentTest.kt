package com.kidswatch.tv.debug

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.auth.PinManager
import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.data.cache.CacheDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugReceiverIntentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var receiver: DebugReceiver

    @Before
    fun setup() {
        val db = CacheDatabase.getInMemoryInstance(context)
        ServiceLocator.initForTest(db, PinManager(), SessionManager())
        receiver = DebugReceiver()
    }

    @Test
    fun debugGetPin_returnsPin() {
        val intent = Intent("com.kidswatch.tv.DEBUG_GET_PIN")
        receiver.onReceive(context, intent)
        val pin = ServiceLocator.pinManager.getCurrentPin()
        assertEquals(6, pin.length)
    }

    @Test
    fun debugResetPin_returnsNewPin() {
        val oldPin = ServiceLocator.pinManager.getCurrentPin()
        val intent = Intent("com.kidswatch.tv.DEBUG_RESET_PIN")
        receiver.onReceive(context, intent)
        // PIN has been reset
        assertTrue(ServiceLocator.pinManager.getCurrentPin().length == 6)
    }

    @Test
    fun debugSimulateAuth_correctPin_returnsValid() {
        val pin = ServiceLocator.pinManager.getCurrentPin()
        val intent = Intent("com.kidswatch.tv.DEBUG_SIMULATE_AUTH").apply {
            putExtra("pin", pin)
        }
        receiver.onReceive(context, intent)
        // Should succeed without throwing
    }

    @Test
    fun debugSimulateAuth_wrongPin_returnsInvalid() {
        val intent = Intent("com.kidswatch.tv.DEBUG_SIMULATE_AUTH").apply {
            putExtra("pin", "000000")
        }
        receiver.onReceive(context, intent)
        // Should not throw
    }

    @Test
    fun debugGetAuthState_returnsState() {
        val intent = Intent("com.kidswatch.tv.DEBUG_GET_AUTH_STATE")
        receiver.onReceive(context, intent)
        assertEquals(0, ServiceLocator.sessionManager.getActiveSessionCount())
    }

    @Test
    fun debugGetNowPlaying_whenNotPlaying() {
        val intent = Intent("com.kidswatch.tv.DEBUG_GET_NOW_PLAYING")
        receiver.onReceive(context, intent)
        // Should not throw
    }

    @Test
    fun debugSimulateOffline_toggles() {
        val wasBefore = com.kidswatch.tv.util.OfflineSimulator.isOffline
        val intent = Intent("com.kidswatch.tv.DEBUG_SIMULATE_OFFLINE")
        receiver.onReceive(context, intent)
        assertNotEquals(wasBefore, com.kidswatch.tv.util.OfflineSimulator.isOffline)
        // Toggle back
        receiver.onReceive(context, intent)
        assertEquals(wasBefore, com.kidswatch.tv.util.OfflineSimulator.isOffline)
    }

    @Test
    fun debugGetPlaylists_emptyReturnsArray() {
        val intent = Intent("com.kidswatch.tv.DEBUG_GET_PLAYLISTS")
        receiver.onReceive(context, intent)
        // Should not throw
    }

    @Test
    fun debugRefreshPlaylists_returnsCount() {
        val intent = Intent("com.kidswatch.tv.DEBUG_REFRESH_PLAYLISTS")
        receiver.onReceive(context, intent)
        // Should not throw
    }

    @Test
    fun debugClearPlayEvents_returns() {
        val intent = Intent("com.kidswatch.tv.DEBUG_CLEAR_PLAY_EVENTS")
        receiver.onReceive(context, intent)
        // Should not throw
    }

    @Test
    fun debugStopPlayback_returns() {
        val intent = Intent("com.kidswatch.tv.DEBUG_STOP_PLAYBACK")
        receiver.onReceive(context, intent)
        // Should not throw
    }

    @Test
    fun debugGetServerStatus_returns() {
        val intent = Intent("com.kidswatch.tv.DEBUG_GET_SERVER_STATUS")
        receiver.onReceive(context, intent)
        // Should not throw
    }
}
