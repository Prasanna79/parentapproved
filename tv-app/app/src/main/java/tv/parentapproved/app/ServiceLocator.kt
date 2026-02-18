package tv.parentapproved.app

import android.content.Context
import android.content.SharedPreferences
import tv.parentapproved.app.auth.PinManager
import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.auth.SharedPrefsSessionPersistence
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.relay.RelayConfig
import tv.parentapproved.app.relay.RelayConnector

object ServiceLocator {
    lateinit var pinManager: PinManager
    lateinit var sessionManager: SessionManager
    lateinit var database: CacheDatabase
    lateinit var relayConfig: RelayConfig
    lateinit var relayConnector: RelayConnector

    private var initialized = false
    private lateinit var relayPrefs: SharedPreferences

    private const val KEY_RELAY_ENABLED = "relay_enabled"

    fun init(context: Context) {
        if (initialized) return
        database = CacheDatabase.getInstance(context)
        val persistence = SharedPrefsSessionPersistence(
            context.getSharedPreferences("parentapproved_sessions", Context.MODE_PRIVATE)
        )
        sessionManager = SessionManager(persistence = persistence)

        // Relay config
        relayPrefs = context.getSharedPreferences("parentapproved_relay", Context.MODE_PRIVATE)
        relayConfig = RelayConfig(
            prefs = relayPrefs,
            relayUrl = BuildConfig.RELAY_URL,
        )

        // RelayConnector
        relayConnector = RelayConnector(config = relayConfig, appVersion = BuildConfig.VERSION_NAME)

        pinManager = PinManager(
            onPinValidated = { sessionManager.createSession() ?: "" }
        )
        PlayEventRecorder.init(database)
        initialized = true
    }

    fun isRelayEnabled(): Boolean {
        return if (::relayPrefs.isInitialized) {
            relayPrefs.getBoolean(KEY_RELAY_ENABLED, false)
        } else false
    }

    fun setRelayEnabled(enabled: Boolean) {
        relayPrefs.edit().putBoolean(KEY_RELAY_ENABLED, enabled).apply()
        if (enabled) {
            relayConnector.connect()
        } else {
            relayConnector.disconnect()
        }
    }

    fun initForTest(db: CacheDatabase, pin: PinManager, session: SessionManager) {
        database = db
        pinManager = pin
        sessionManager = session
        PlayEventRecorder.init(db)
        initialized = true
    }

    fun isInitialized(): Boolean = initialized
}
