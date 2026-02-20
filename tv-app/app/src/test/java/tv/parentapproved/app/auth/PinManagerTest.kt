package tv.parentapproved.app.auth

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PinManagerTest {

    private var currentTime = 0L
    private lateinit var pinManager: PinManager

    @Before
    fun setup() {
        currentTime = 1000000L
        pinManager = PinManager(clock = { currentTime })
    }

    @Test
    fun generatePin_returns6Digits() {
        val pin = pinManager.getCurrentPin()
        assertEquals(6, pin.length)
        assertTrue("PIN should be all numeric", pin.all { it.isDigit() })
    }

    @Test
    fun generatePin_differentEachTime() {
        val pins = (1..10).map { PinManager(clock = { currentTime }).getCurrentPin() }.toSet()
        assertTrue("At least 2 different PINs from 10 generations", pins.size >= 2)
    }

    @Test
    fun validatePin_correctPinReturnsTrue() {
        val pin = pinManager.getCurrentPin()
        val result = pinManager.validate(pin)
        assertTrue("Correct PIN should return Success", result is PinResult.Success)
    }

    @Test
    fun validatePin_wrongPinReturnsFalse() {
        val result = pinManager.validate("000000")
        assertTrue("Wrong PIN should return Invalid", result is PinResult.Invalid)
    }

    @Test
    fun rateLimiting_locksAfter5Failures() {
        repeat(4) {
            pinManager.validate("wrong!")
        }
        val fifthResult = pinManager.validate("wrong!")
        assertTrue("5th failure should trigger rate limiting", fifthResult is PinResult.RateLimited)
    }

    @Test
    fun rateLimiting_resetsAfterTimeout() {
        repeat(5) { pinManager.validate("wrong!") }
        // Advance past 5 minute lockout
        currentTime += 5 * 60 * 1000L + 1
        val result = pinManager.validate("wrong!")
        assertTrue("After timeout, should allow attempt (Invalid, not RateLimited)", result is PinResult.Invalid)
    }

    @Test
    fun rateLimiting_exponentialBackoff() {
        // First lockout: 5 min
        repeat(5) { pinManager.validate("wrong!") }
        val first = pinManager.validate("wrong!") as PinResult.RateLimited

        // Advance past first lockout
        currentTime += first.retryAfterMs + 1

        // Second lockout: 10 min
        repeat(5) { pinManager.validate("wrong!") }
        val second = pinManager.validate("wrong!") as PinResult.RateLimited
        assertTrue("Second lockout should be longer", second.retryAfterMs > first.retryAfterMs)

        // Advance past second lockout
        currentTime += second.retryAfterMs + 1

        // Third lockout: 20 min
        repeat(5) { pinManager.validate("wrong!") }
        val third = pinManager.validate("wrong!") as PinResult.RateLimited
        assertTrue("Third lockout should be longer than second", third.retryAfterMs > second.retryAfterMs)
    }

    @Test
    fun rateLimiting_successResetsCounter() {
        val pin = pinManager.getCurrentPin()
        repeat(3) { pinManager.validate("wrong!") }
        assertEquals(3, pinManager.getFailedAttempts())

        pinManager.validate(pin)
        assertEquals(0, pinManager.getFailedAttempts())
    }

    @Test
    fun resetPin_generatesNewPin() {
        val oldPin = pinManager.getCurrentPin()
        // Reset enough times to ensure a different PIN (statistically near-certain)
        var different = false
        repeat(10) {
            val newPin = pinManager.resetPin()
            if (newPin != oldPin) different = true
        }
        assertTrue("Reset should eventually generate a different PIN", different)
    }

    @Test
    fun resetPin_invalidatesOldPin() {
        val oldPin = pinManager.getCurrentPin()
        pinManager.resetPin()
        val result = pinManager.validate(oldPin)
        // Old PIN should now fail (unless by extreme coincidence it's the same)
        // We test the mechanism: currentPin changed, so validate against old should fail
        val newPin = pinManager.getCurrentPin()
        if (oldPin != newPin) {
            assertTrue("Old PIN should fail after reset", result is PinResult.Invalid)
        }
    }

    @Test
    fun isLockedOut_returnsTrueWhenLocked() {
        repeat(5) { pinManager.validate("wrong!") }
        assertTrue("Should be locked out after 5 failures", pinManager.isLockedOut())
    }

    @Test
    fun lockoutPersistence_survivesRestart() {
        val fakePersistence = FakePinLockoutPersistence()
        val pm1 = PinManager(clock = { currentTime }, lockoutPersistence = fakePersistence)
        val pin = pm1.getCurrentPin()

        // Fail 3 times
        repeat(3) { pm1.validate("wrong!") }
        assertEquals(3, pm1.getFailedAttempts())

        // Simulate restart: create new PinManager with same persistence
        val pm2 = PinManager(clock = { currentTime }, lockoutPersistence = fakePersistence)
        assertEquals(3, pm2.getFailedAttempts())
    }

    @Test
    fun lockoutPersistence_lockoutSurvivesRestart() {
        val fakePersistence = FakePinLockoutPersistence()
        val pm1 = PinManager(clock = { currentTime }, lockoutPersistence = fakePersistence)

        // Trigger lockout
        repeat(5) { pm1.validate("wrong!") }
        assertTrue(pm1.isLockedOut())

        // Simulate restart
        val pm2 = PinManager(clock = { currentTime }, lockoutPersistence = fakePersistence)
        assertTrue("Lockout should survive restart", pm2.isLockedOut())
    }

    private class FakePinLockoutPersistence : PinLockoutPersistence {
        private var failedAttempts = 0
        private var lockoutUntil = 0L
        private var lockoutCount = 0

        override fun save(failedAttempts: Int, lockoutUntil: Long, lockoutCount: Int) {
            this.failedAttempts = failedAttempts
            this.lockoutUntil = lockoutUntil
            this.lockoutCount = lockoutCount
        }

        override fun loadFailedAttempts(): Int = failedAttempts
        override fun loadLockoutUntil(): Long = lockoutUntil
        override fun loadLockoutCount(): Int = lockoutCount
    }
}
