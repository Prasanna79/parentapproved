package tv.parentapproved.app.auth

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
        // Advance past 90 days
        currentTime += 90L * 24 * 60 * 60 * 1000 + 1
        assertFalse(sessionManager.validateSession(token))
    }

    @Test
    fun maxConcurrentSessions_rejectsExcess() {
        repeat(20) { sessionManager.createSession() }
        val excess = sessionManager.createSession()
        assertNull("21st session should be rejected", excess)
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

    @Test
    fun refreshSession_validToken_returnsNewToken() {
        val token = sessionManager.createSession()!!
        val newToken = sessionManager.refreshSession(token)
        assertNotNull(newToken)
        assertNotEquals(token, newToken)
        // Old token should be invalid
        assertFalse(sessionManager.validateSession(token))
        // New token should be valid
        assertTrue(sessionManager.validateSession(newToken!!))
    }

    @Test
    fun refreshSession_invalidToken_returnsNull() {
        val result = sessionManager.refreshSession("nonexistent")
        assertNull(result)
    }

    @Test
    fun refreshSession_expiredToken_returnsNull() {
        val token = sessionManager.createSession()!!
        currentTime += 91L * 24 * 60 * 60 * 1000 // past 90-day TTL
        val result = sessionManager.refreshSession(token)
        assertNull(result)
    }

    @Test
    fun refreshSession_preservesSessionCount() {
        val token = sessionManager.createSession()!!
        sessionManager.createSession()
        assertEquals(2, sessionManager.getActiveSessionCount())
        sessionManager.refreshSession(token)
        assertEquals(2, sessionManager.getActiveSessionCount())
    }

    @Test
    fun sessionExpiry_90dayTtl() {
        val token = sessionManager.createSession()!!
        // At 89 days, still valid
        currentTime += 89L * 24 * 60 * 60 * 1000
        assertTrue(sessionManager.validateSession(token))
        // At 91 days, expired
        currentTime += 2L * 24 * 60 * 60 * 1000
        assertFalse(sessionManager.validateSession(token))
    }

    @Test
    fun persistence_savesOnCreate() {
        val fakePersistence = FakeSessionPersistence()
        val sm = SessionManager(clock = { currentTime }, persistence = fakePersistence)
        sm.createSession()
        assertEquals(1, fakePersistence.savedSessions.size)
    }

    @Test
    fun persistence_loadsOnInit() {
        val fakePersistence = FakeSessionPersistence()
        // Pre-seed with a session
        fakePersistence.savedSessions["existing-token"] = currentTime
        val sm = SessionManager(clock = { currentTime }, persistence = fakePersistence)
        assertTrue(sm.validateSession("existing-token"))
    }

    @Test
    fun persistence_savesOnInvalidateAll() {
        val fakePersistence = FakeSessionPersistence()
        val sm = SessionManager(clock = { currentTime }, persistence = fakePersistence)
        sm.createSession()
        sm.invalidateAll()
        assertTrue(fakePersistence.savedSessions.isEmpty())
    }

    @Test
    fun persistence_savesOnRefresh() {
        val fakePersistence = FakeSessionPersistence()
        val sm = SessionManager(clock = { currentTime }, persistence = fakePersistence)
        val token = sm.createSession()!!
        val newToken = sm.refreshSession(token)!!
        assertFalse(fakePersistence.savedSessions.containsKey(token))
        assertTrue(fakePersistence.savedSessions.containsKey(newToken))
    }

    @Test
    fun concurrentSessionValidation_doesNotThrow() {
        // Create several sessions
        val tokens = (1..10).mapNotNull { sessionManager.createSession() }
        assertEquals(10, tokens.size)

        // Validate all concurrently from multiple threads
        val threads = tokens.map { token ->
            Thread {
                repeat(100) {
                    sessionManager.validateSession(token)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        // If we reach here without ConcurrentModificationException, test passes
    }

    @Test
    fun persistence_null_doesNotCrash() {
        // Default constructor with no persistence should work fine
        val sm = SessionManager(clock = { currentTime })
        val token = sm.createSession()!!
        assertTrue(sm.validateSession(token))
        sm.refreshSession(token)
        sm.invalidateAll()
    }

    private class FakeSessionPersistence : SessionPersistence {
        val savedSessions = HashMap<String, Long>()

        override fun save(sessions: Map<String, Long>) {
            savedSessions.clear()
            savedSessions.putAll(sessions)
        }

        override fun load(): Map<String, Long> = HashMap(savedSessions)
    }
}
