package com.kidswatch.tv

import android.content.Context
import com.kidswatch.tv.auth.PinManager
import com.kidswatch.tv.auth.SessionManager
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.events.PlayEventRecorder

object ServiceLocator {
    lateinit var pinManager: PinManager
    lateinit var sessionManager: SessionManager
    lateinit var database: CacheDatabase

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        database = CacheDatabase.getInstance(context)
        sessionManager = SessionManager()
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
