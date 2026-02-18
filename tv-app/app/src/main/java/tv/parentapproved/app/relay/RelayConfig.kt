package tv.parentapproved.app.relay

import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.UUID

class RelayConfig(
    private val prefs: SharedPreferences,
    val relayUrl: String = DEFAULT_RELAY_URL,
) {
    companion object {
        const val DEFAULT_RELAY_URL = "https://relay.parentapproved.tv"
        private const val KEY_TV_ID = "relay_tv_id"
        private const val KEY_TV_SECRET = "relay_tv_secret"
    }

    val tvId: String
    var tvSecret: String
        private set

    init {
        tvId = prefs.getString(KEY_TV_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TV_ID, id).apply()
            id
        }
        tvSecret = prefs.getString(KEY_TV_SECRET, null) ?: run {
            val secret = generateSecret()
            prefs.edit().putString(KEY_TV_SECRET, secret).apply()
            secret
        }
    }

    fun rotateTvSecret(): String {
        tvSecret = generateSecret()
        prefs.edit().putString(KEY_TV_SECRET, tvSecret).apply()
        return tvSecret
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
