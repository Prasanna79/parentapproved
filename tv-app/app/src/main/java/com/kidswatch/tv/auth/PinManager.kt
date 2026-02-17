package com.kidswatch.tv.auth

sealed class PinResult {
    data class Success(val token: String) : PinResult()
    data class Invalid(val attemptsRemaining: Int) : PinResult()
    data class RateLimited(val retryAfterMs: Long) : PinResult()
}

class PinManager(
    private val clock: () -> Long = System::currentTimeMillis,
    private val onPinValidated: ((String) -> String)? = null,
) {
    private var currentPin: String = generatePin()
    private var failedAttempts: Int = 0
    private var lockoutUntil: Long = 0
    private var lockoutCount: Int = 0

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 5 * 60 * 1000L // 5 minutes
    }

    fun generatePin(): String {
        val pin = (1..6).map { (0..9).random() }.joinToString("")
        currentPin = pin
        return pin
    }

    fun getCurrentPin(): String = currentPin

    fun validate(pin: String): PinResult {
        val now = clock()

        if (now < lockoutUntil) {
            return PinResult.RateLimited(lockoutUntil - now)
        }

        // Reset failed attempts counter when lockout has expired
        if (lockoutUntil > 0 && now >= lockoutUntil) {
            failedAttempts = 0
            lockoutUntil = 0
        }

        if (pin == currentPin) {
            failedAttempts = 0
            lockoutCount = 0
            val token = onPinValidated?.invoke(pin) ?: ""
            return PinResult.Success(token)
        }

        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            lockoutCount++
            val multiplier = 1L shl (lockoutCount - 1).coerceAtMost(10)
            lockoutUntil = now + BASE_LOCKOUT_MS * multiplier
            return PinResult.RateLimited(BASE_LOCKOUT_MS * multiplier)
        }

        return PinResult.Invalid(MAX_ATTEMPTS - failedAttempts)
    }

    fun resetPin(): String {
        val newPin = generatePin()
        failedAttempts = 0
        lockoutCount = 0
        lockoutUntil = 0
        return newPin
    }

    fun isLockedOut(): Boolean = clock() < lockoutUntil

    fun getFailedAttempts(): Int = failedAttempts
}
