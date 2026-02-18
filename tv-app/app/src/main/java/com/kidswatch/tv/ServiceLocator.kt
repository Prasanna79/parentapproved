package com.kidswatch.tv

import android.content.Context
import com.kidswatch.tv.auth.PinManager
import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.auth.SharedPrefsSessionPersistence
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.events.PlayEventRecorder
import com.kidswatch.tv.relay.RelayConfig
import com.kidswatch.tv.relay.RelayConnector

object ServiceLocator {
    lateinit var pinManager: PinManager
    lateinit var sessionManager: SessionManager
    lateinit var database: CacheDatabase
    lateinit var relayConfig: RelayConfig
    lateinit var relayConnector: RelayConnector

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        database = CacheDatabase.getInstance(context)
        val persistence = SharedPrefsSessionPersistence(
            context.getSharedPreferences("kidswatch_sessions", Context.MODE_PRIVATE)
        )
        sessionManager = SessionManager(persistence = persistence)

        // Relay config
        val relayPrefs = context.getSharedPreferences("kidswatch_relay", Context.MODE_PRIVATE)
        relayConfig = RelayConfig(
            prefs = relayPrefs,
            relayUrl = BuildConfig.RELAY_URL,
        )

        // RelayConnector
        relayConnector = RelayConnector(config = relayConfig)

        pinManager = PinManager(
            onPinValidated = { sessionManager.createSession() ?: "" }
        )
        PlayEventRecorder.init(database)
        initialized = true
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
