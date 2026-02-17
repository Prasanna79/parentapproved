package com.kidswatch.tv.auth

import java.security.SecureRandom

class SessionManager(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxSessions: Int = 5,
    private val ttlMs: Long = 30L * 24 * 60 * 60 * 1000, // 30 days
) {
    private val sessions = HashMap<String, Long>() // token -> createdAt
    private val random = SecureRandom()

    fun createSession(): String? {
        pruneExpired()
        if (sessions.size >= maxSessions) return null

        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        sessions[token] = clock()
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

    fun invalidateAll() {
        sessions.clear()
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
