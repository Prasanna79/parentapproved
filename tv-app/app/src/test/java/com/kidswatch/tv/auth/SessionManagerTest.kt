package com.kidswatch.tv.auth

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private var currentTime = 0L
    private lateinit var sessionManager: SessionManager

    @Before
    fun setup() {
        currentTime = 1000000L
        sessionManager = SessionManager(clock = { currentTime })
    }

    @Test
    fun createSession_returnsTokenString() {
        val token = sessionManager.createSession()
        assertNotNull(token)
        assertEquals("Token should be 64 hex chars", 64, token!!.length)
        assertTrue("Token should be hex", token.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun validateSession_validTokenReturnsTrue() {
        val token = sessionManager.createSession()!!
        assertTrue(sessionManager.validateSession(token))
    }

    @Test
    fun validateSession_unknownTokenReturnsFalse() {
        assertFalse(sessionManager.validateSession("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"))
    }

    @Test
    fun sessionExpiry_expiredSessionInvalid() {
        val token = sessionManager.createSession()!!
        // Advance past 30 days
        currentTime += 30L * 24 * 60 * 60 * 1000 + 1
        assertFalse(sessionManager.validateSession(token))
    }

    @Test
    fun maxConcurrentSessions_rejects6th() {
        repeat(5) { sessionManager.createSession() }
        val sixth = sessionManager.createSession()
        assertNull("6th session should be rejected", sixth)
    }

    @Test
    fun invalidateAll_clearsAllSessions() {
        val tokens = (1..3).map { sessionManager.createSession()!! }
        sessionManager.invalidateAll()
        tokens.forEach { assertFalse(sessionManager.validateSession(it)) }
        assertEquals(0, sessionManager.getActiveSessionCount())
    }

    @Test
    fun resetPinFlow_invalidatesAllSessions() {
        // Simulates what SettingsScreen and DebugReceiver do:
        // resetPin() + invalidateAll() ensures old tokens stop working
        val pinManager = PinManager(clock = { currentTime })
        val token1 = sessionManager.createSession()!!
        val token2 = sessionManager.createSession()!!
        assertTrue(sessionManager.validateSession(token1))

        // Reset PIN should clear sessions (call site responsibility)
        pinManager.resetPin()
        sessionManager.invalidateAll()

        assertFalse(sessionManager.validateSession(token1))
        assertFalse(sessionManager.validateSession(token2))
        // New session can be created after reset
        val newToken = sessionManager.createSession()
        assertNotNull(newToken)
        assertTrue(sessionManager.validateSession(newToken!!))
    }

    @Test
    fun getActiveSessionCount_tracksCorrectly() {
        assertEquals(0, sessionManager.getActiveSessionCount())
        sessionManager.createSession()
        assertEquals(1, sessionManager.getActiveSessionCount())
        sessionManager.createSession()
        assertEquals(2, sessionManager.getActiveSessionCount())
        sessionManager.invalidateAll()
        assertEquals(0, sessionManager.getActiveSessionCount())
    }
}
