package tv.parentapproved.app.relay

import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RelayConfigTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
    }

    @Test
    fun firstLaunch_generatesTvId() {
        every { prefs.getString("relay_tv_id", null) } returns null
        every { prefs.getString("relay_tv_secret", null) } returns null
        val config = RelayConfig(prefs)
        assertNotNull(config.tvId)
        assertTrue(config.tvId.isNotEmpty())
        verify { editor.putString("relay_tv_id", config.tvId) }
    }

    @Test
    fun firstLaunch_generatesTvSecret() {
        every { prefs.getString("relay_tv_id", null) } returns null
        every { prefs.getString("relay_tv_secret", null) } returns null
        val config = RelayConfig(prefs)
        assertNotNull(config.tvSecret)
        assertEquals(64, config.tvSecret.length) // 32 bytes = 64 hex chars
        assertTrue(config.tvSecret.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun subsequentLaunch_loadsExistingTvId() {
        every { prefs.getString("relay_tv_id", null) } returns "existing-id"
        every { prefs.getString("relay_tv_secret", null) } returns "existing-secret"
        val config = RelayConfig(prefs)
        assertEquals("existing-id", config.tvId)
    }

    @Test
    fun subsequentLaunch_loadsExistingTvSecret() {
        every { prefs.getString("relay_tv_id", null) } returns "existing-id"
        every { prefs.getString("relay_tv_secret", null) } returns "existing-secret"
        val config = RelayConfig(prefs)
        assertEquals("existing-secret", config.tvSecret)
    }

    @Test
    fun rotateTvSecret_generatesNewSecret() {
        every { prefs.getString("relay_tv_id", null) } returns "existing-id"
        every { prefs.getString("relay_tv_secret", null) } returns "old-secret"
        val config = RelayConfig(prefs)
        val oldSecret = config.tvSecret
        val newSecret = config.rotateTvSecret()
        assertNotEquals(oldSecret, newSecret)
        assertEquals(64, newSecret.length)
        verify { editor.putString("relay_tv_secret", newSecret) }
    }

    @Test
    fun rotateTvSecret_preservesTvId() {
        every { prefs.getString("relay_tv_id", null) } returns "my-tv-id"
        every { prefs.getString("relay_tv_secret", null) } returns "old-secret"
        val config = RelayConfig(prefs)
        config.rotateTvSecret()
        assertEquals("my-tv-id", config.tvId)
    }

    @Test
    fun relayUrl_defaultsToProduction() {
        every { prefs.getString("relay_tv_id", null) } returns "id"
        every { prefs.getString("relay_tv_secret", null) } returns "secret"
        val config = RelayConfig(prefs)
        assertEquals("https://relay.parentapproved.tv", config.relayUrl)
    }

    @Test
    fun relayUrl_canBeOverridden() {
        every { prefs.getString("relay_tv_id", null) } returns "id"
        every { prefs.getString("relay_tv_secret", null) } returns "secret"
        val config = RelayConfig(prefs, relayUrl = "https://staging.workers.dev")
        assertEquals("https://staging.workers.dev", config.relayUrl)
    }
}
