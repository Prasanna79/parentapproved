package tv.parentapproved.app.auth

import android.content.SharedPreferences

class SharedPrefsPinLockoutPersistence(
    private val prefs: SharedPreferences,
) : PinLockoutPersistence {

    companion object {
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_LOCKOUT_COUNT = "pin_lockout_count"
    }

    override fun save(failedAttempts: Int, lockoutUntil: Long, lockoutCount: Int) {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
            .putInt(KEY_LOCKOUT_COUNT, lockoutCount)
            .apply()
    }

    override fun loadFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    override fun loadLockoutUntil(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
    override fun loadLockoutCount(): Int = prefs.getInt(KEY_LOCKOUT_COUNT, 0)
}
