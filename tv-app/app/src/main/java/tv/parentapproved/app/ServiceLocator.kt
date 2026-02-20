package tv.parentapproved.app

import android.content.Context
import android.content.SharedPreferences
import tv.parentapproved.app.auth.PinManager
import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.auth.SharedPrefsPinLockoutPersistence
import tv.parentapproved.app.auth.SharedPrefsSessionPersistence
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.relay.RelayConfig
import tv.parentapproved.app.relay.RelayConnector
import tv.parentapproved.app.timelimits.RoomTimeLimitStore
import tv.parentapproved.app.timelimits.RoomWatchTimeProvider
import tv.parentapproved.app.timelimits.TimeLimitManager

object ServiceLocator {
    lateinit var pinManager: PinManager
    lateinit var sessionManager: SessionManager
    lateinit var database: CacheDatabase
    lateinit var relayConfig: RelayConfig
    lateinit var relayConnector: RelayConnector
    lateinit var timeLimitManager: TimeLimitManager

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

        val pinLockoutPersistence = SharedPrefsPinLockoutPersistence(
            context.getSharedPreferences("parentapproved_pin_lockout", Context.MODE_PRIVATE)
        )
        pinManager = PinManager(
            onPinValidated = { sessionManager.createSession() ?: "" },
            lockoutPersistence = pinLockoutPersistence,
        )
        PlayEventRecorder.init(database)

        val timeLimitStore = RoomTimeLimitStore(database.timeLimitDao())
        val watchTimeProvider = RoomWatchTimeProvider(
            playEventDao = database.playEventDao(),
            currentVideoElapsedProvider = { PlayEventRecorder.getElapsedMs() },
        )
        timeLimitManager = TimeLimitManager(
            store = timeLimitStore,
            watchTimeProvider = watchTimeProvider,
        )

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

    fun initForTest(
        db: CacheDatabase,
        pin: PinManager,
        session: SessionManager,
        timeLimit: TimeLimitManager? = null,
    ) {
        database = db
        pinManager = pin
        sessionManager = session
        PlayEventRecorder.init(db)
        if (timeLimit != null) {
            timeLimitManager = timeLimit
        } else {
            // No-op default: no limits configured, always allowed
            val noOpStore = object : tv.parentapproved.app.timelimits.TimeLimitStore {
                override suspend fun getConfig() = null
                override suspend fun saveConfig(config: tv.parentapproved.app.timelimits.TimeLimitConfig) {}
                override suspend fun updateManualLock(locked: Boolean) {}
                override suspend fun updateBonus(minutes: Int, date: String) {}
            }
            val noOpWatch = object : tv.parentapproved.app.timelimits.WatchTimeProvider {
                override suspend fun getTodayWatchSeconds() = 0
            }
            timeLimitManager = TimeLimitManager(store = noOpStore, watchTimeProvider = noOpWatch)
        }
        initialized = true
    }

    fun isInitialized(): Boolean = initialized
}
