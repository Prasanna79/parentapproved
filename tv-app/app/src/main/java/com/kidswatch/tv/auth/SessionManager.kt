package com.kidswatch.tv.auth

import java.security.SecureRandom

class SessionManager(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxSessions: Int = 5,
    private val ttlMs: Long = 90L * 24 * 60 * 60 * 1000, // 90 days
    private val persistence: SessionPersistence? = null,
) {
    private val sessions = HashMap<String, Long>() // token -> createdAt
    private val random = SecureRandom()

    init {
        persistence?.load()?.let { loaded ->
            sessions.putAll(loaded)
            pruneExpired()
        }
    }

    fun createSession(): String? {
        pruneExpired()
        if (sessions.size >= maxSessions) return null

        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        sessions[token] = clock()
        persistence?.save(sessions)
        return token
    }

    fun validateSession(token: String): Boolean {
        val createdAt = sessions[token] ?: return false
        if (clock() - createdAt > ttlMs) {
            sessions.remove(token)
            return false
        }
        return true
    }

    fun refreshSession(token: String): String? {
        if (!validateSession(token)) return null
        sessions.remove(token)

        pruneExpired()
        if (sessions.size >= maxSessions) return null

        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val newToken = bytes.joinToString("") { "%02x".format(it) }
        sessions[newToken] = clock()
        persistence?.save(sessions)
        return newToken
    }

    fun invalidateAll() {
        sessions.clear()
        persistence?.save(sessions)
    }

    fun getActiveSessionCount(): Int {
        pruneExpired()
        return sessions.size
    }

    private fun pruneExpired() {
        val now = clock()
        sessions.entries.removeAll { now - it.value > ttlMs }
    }
}
