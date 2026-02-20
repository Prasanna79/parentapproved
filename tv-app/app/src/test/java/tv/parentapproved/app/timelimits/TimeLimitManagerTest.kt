package tv.parentapproved.app.timelimits

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimeLimitManagerTest {

    private var currentTime = 0L
    private lateinit var store: FakeTimeLimitStore
    private lateinit var watchTime: FakeWatchTimeProvider
    private lateinit var manager: TimeLimitManager

    @Before
    fun setup() {
        // Wednesday 2026-02-18 at 14:00 (2 PM) local time
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(14, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        store = FakeTimeLimitStore()
        watchTime = FakeWatchTimeProvider()
        manager = TimeLimitManager(
            clock = { currentTime },
            store = store,
            watchTimeProvider = watchTime,
        )
    }

    // --- Daily limit tests ---

    @Test
    fun canPlay_noConfig_returnsAllowed() = runTest {
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_noLimitSet_returnsAllowed() = runTest {
        store.storedConfig = TimeLimitConfig() // all defaults = no limits
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_underLimit_returnsAllowed() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 30 * 60 // 30 min used of 60 min limit
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_exactlyAtLimit_returnsBlocked_DAILY_LIMIT() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 60 * 60 // exactly 60 min
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.DAILY_LIMIT, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_overLimit_returnsBlocked_DAILY_LIMIT() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 65 * 60 // 65 min, over 60 limit
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.DAILY_LIMIT, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_5minRemaining_returnsWarning() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 55 * 60 // 55 min used, 5 min left
        val status = manager.canPlay()
        assertTrue("Should return Warning, got $status", status is TimeLimitStatus.Warning)
        assertEquals(5, (status as TimeLimitStatus.Warning).minutesLeft)
    }

    @Test
    fun canPlay_6minRemaining_returnsAllowed() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 54 * 60 // 54 min used, 6 min left
        val status = manager.canPlay()
        assertTrue("6 min remaining should be Allowed, got $status", status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_saturdayLimit_usedOnSaturday() = runTest {
        // Set time to Saturday
        currentTime = LocalDate.of(2026, 2, 21) // Saturday
            .atTime(14, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(
                DayOfWeek.WEDNESDAY to 60,
                DayOfWeek.SATURDAY to 120,
            ),
        )
        watchTime.watchSeconds = 90 * 60 // 90 min — over Wed limit but under Sat limit
        val status = manager.canPlay()
        assertTrue("Saturday has 120 min limit, 90 used should be Allowed", status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_noLimitForToday_returnsAllowed() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.MONDAY to 60), // only Monday has a limit
        )
        watchTime.watchSeconds = 999 * 60 // lots of watch time
        val status = manager.canPlay() // Wednesday — no limit set
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun getRemainingMinutes_calculatesCorrectly() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 45 * 60
        val remaining = manager.getRemainingMinutes()
        assertEquals(15, remaining)
    }

    @Test
    fun getRemainingMinutes_noLimit_returnsNull() = runTest {
        store.storedConfig = TimeLimitConfig() // no limits
        val remaining = manager.getRemainingMinutes()
        assertNull(remaining)
    }

    @Test
    fun getRemainingMinutes_accountsForCurrentVideoElapsed() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 50 * 60 // 50 min from DB (includes current video via provider)
        val remaining = manager.getRemainingMinutes()
        assertEquals(10, remaining)
    }

    // --- Bedtime tests ---

    @Test
    fun canPlay_duringBedtime_returnsBlocked_BEDTIME() = runTest {
        // Set time to 21:00 (9 PM)
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30, // 20:30
            bedtimeEndMin = 7 * 60,          // 07:00
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.BEDTIME, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_outsideBedtime_returnsAllowed() = runTest {
        // 14:00 is outside 20:30-07:00
        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_bedtimeSpansMidnight_beforeMidnight_blocked() = runTest {
        // 23:00, bedtime 22:00-06:00
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(23, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 22 * 60,
            bedtimeEndMin = 6 * 60,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.BEDTIME, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_bedtimeSpansMidnight_afterMidnight_blocked() = runTest {
        // 03:00, bedtime 22:00-06:00
        currentTime = LocalDate.of(2026, 2, 19)
            .atTime(3, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 22 * 60,
            bedtimeEndMin = 6 * 60,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.BEDTIME, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_bedtimeSpansMidnight_afterEnd_allowed() = runTest {
        // 07:00, bedtime 22:00-06:00
        currentTime = LocalDate.of(2026, 2, 19)
            .atTime(7, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 22 * 60,
            bedtimeEndMin = 6 * 60,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_bedtimeOff_returnsAllowed() = runTest {
        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = -1,
            bedtimeEndMin = -1,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun canPlay_exactlyAtBedtimeStart_blocked() = runTest {
        // 20:30 exactly
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(20, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.BEDTIME, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_exactlyAtBedtimeEnd_allowed() = runTest {
        // 07:00 exactly
        currentTime = LocalDate.of(2026, 2, 19)
            .atTime(7, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    // --- Manual lock tests ---

    @Test
    fun canPlay_manuallyLocked_returnsBlocked_MANUAL_LOCK() = runTest {
        store.storedConfig = TimeLimitConfig(manuallyLocked = true)
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.MANUAL_LOCK, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_notManuallyLocked_returnsAllowed() = runTest {
        store.storedConfig = TimeLimitConfig(manuallyLocked = false)
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun setManualLock_true_blocksImmediately() = runTest {
        store.storedConfig = TimeLimitConfig()
        manager.setManualLock(true)
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.MANUAL_LOCK, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun setManualLock_false_unblocks() = runTest {
        store.storedConfig = TimeLimitConfig(manuallyLocked = true)
        manager.setManualLock(false)
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Allowed)
    }

    @Test
    fun setManualLock_false_doesNotOverrideDailyLimit() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
            manuallyLocked = true,
        )
        watchTime.watchSeconds = 65 * 60 // over limit
        manager.setManualLock(false) // unlock manual lock
        val status = manager.canPlay()
        // Daily limit still exceeded
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.DAILY_LIMIT, (status as TimeLimitStatus.Blocked).reason)
    }

    // --- Bonus time tests ---

    @Test
    fun grantBonus_extendsDailyLimit() = runTest {
        val today = LocalDate.of(2026, 2, 18).toString()
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 60 * 60 // at limit

        // Should be blocked
        assertTrue(manager.canPlay() is TimeLimitStatus.Blocked)

        // Grant 15 min bonus
        manager.grantBonusMinutes(15)

        // Should be allowed now
        val status = manager.canPlay()
        assertTrue("Bonus should extend limit, got $status", status is TimeLimitStatus.Allowed || status is TimeLimitStatus.Warning)
    }

    @Test
    fun grantBonus_15plus15_accumulates() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 60 * 60

        manager.grantBonusMinutes(15)
        manager.grantBonusMinutes(15)

        // Should have 30 min bonus total
        val remaining = manager.getRemainingMinutes()
        assertEquals(30, remaining)
    }

    @Test
    fun grantBonus_unlocksBlockedDevice() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 65 * 60 // 5 min over

        assertTrue(manager.canPlay() is TimeLimitStatus.Blocked)

        manager.grantBonusMinutes(15)

        // 15 bonus - 5 over = 10 remaining
        val status = manager.canPlay()
        assertTrue("Should be allowed after bonus, got $status", status is TimeLimitStatus.Allowed || status is TimeLimitStatus.Warning)
    }

    @Test
    fun grantBonus_overridesBedtime() = runTest {
        // 21:00, bedtime 20:30-07:00
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
        )

        assertTrue(manager.canPlay() is TimeLimitStatus.Blocked)

        manager.grantBonusMinutes(15)

        val status = manager.canPlay()
        assertTrue("Bonus should override bedtime, got $status",
            status is TimeLimitStatus.Allowed || status is TimeLimitStatus.Warning)
    }

    @Test
    fun grantBonus_duringBedtime_noDailyLimit_createsTemporaryAllowance() = runTest {
        // 21:00, bedtime active, no daily limit set
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
        )
        watchTime.watchSeconds = 0

        manager.grantBonusMinutes(15)

        val status = manager.canPlay()
        assertTrue("Bonus during bedtime with no daily limit should allow, got $status",
            status is TimeLimitStatus.Allowed || status is TimeLimitStatus.Warning)
    }

    @Test
    fun grantBonus_duringBedtime_expiresAfterActivePlayback() = runTest {
        // 21:00, bedtime active
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
        )

        manager.grantBonusMinutes(15)
        assertTrue(manager.canPlay() !is TimeLimitStatus.Blocked)

        // Simulate 16 min of active playback
        watchTime.watchSeconds = 16 * 60

        // Bonus exhausted, bedtime still active → blocked
        val status = manager.canPlay()
        assertTrue("Bonus expired during bedtime should re-block, got $status",
            status is TimeLimitStatus.Blocked)
    }

    @Test
    fun grantBonus_resetsNextDay() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 60 * 60
        manager.grantBonusMinutes(15)

        // Advance to Thursday
        currentTime = LocalDate.of(2026, 2, 19)
            .atTime(10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        watchTime.watchSeconds = 0 // new day

        // Bonus should not carry over — if Thursday has no limit, should be allowed regardless
        // Add a Thursday limit to test bonus reset
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(
                DayOfWeek.WEDNESDAY to 60,
                DayOfWeek.THURSDAY to 30,
            ),
            bonusMinutes = 15, // still set from Wednesday
            bonusDate = LocalDate.of(2026, 2, 18).toString(), // Wednesday's date
        )
        watchTime.watchSeconds = 30 * 60 // at Thursday's limit

        val status = manager.canPlay()
        assertTrue("Wednesday's bonus should not apply on Thursday, got $status",
            status is TimeLimitStatus.Blocked)
    }

    @Test
    fun grantBonus_doesNotOverrideManualLock() = runTest {
        store.storedConfig = TimeLimitConfig(manuallyLocked = true)
        manager.grantBonusMinutes(15)

        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.MANUAL_LOCK, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun grantBonus_cappedAt240minutes() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        manager.grantBonusMinutes(300) // try to grant 5 hours

        val config = store.storedConfig!!
        assertTrue("Bonus should be capped at 240", config.bonusMinutes <= 240)
    }

    // --- Priority tests ---

    @Test
    fun canPlay_manualLockPlusBedtime_reasonIsManualLock() = runTest {
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
            manuallyLocked = true,
        )
        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.MANUAL_LOCK, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_dailyLimitPlusBedtime_reasonIsDailyLimit() = runTest {
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
        )
        watchTime.watchSeconds = 65 * 60

        val status = manager.canPlay()
        assertTrue(status is TimeLimitStatus.Blocked)
        // Daily limit takes priority over bedtime
        assertEquals(LockReason.DAILY_LIMIT, (status as TimeLimitStatus.Blocked).reason)
    }

    @Test
    fun canPlay_bonusOverridesBedtime_butNotManualLock() = runTest {
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        store.storedConfig = TimeLimitConfig(
            bedtimeStartMin = 20 * 60 + 30,
            bedtimeEndMin = 7 * 60,
            manuallyLocked = true,
        )
        manager.grantBonusMinutes(15)

        val status = manager.canPlay()
        // Manual lock not overridden by bonus
        assertTrue(status is TimeLimitStatus.Blocked)
        assertEquals(LockReason.MANUAL_LOCK, (status as TimeLimitStatus.Blocked).reason)
    }

    // --- Midnight rollover tests ---

    @Test
    fun midnightRollover_resetsDailyAccumulator() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
        )
        watchTime.watchSeconds = 60 * 60 // at limit on Wednesday

        assertTrue(manager.canPlay() is TimeLimitStatus.Blocked)

        // Advance to Thursday
        currentTime = LocalDate.of(2026, 2, 19)
            .atTime(0, 1)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        watchTime.watchSeconds = 0 // new day, fresh counter

        // No Thursday limit set → allowed
        val status = manager.canPlay()
        assertTrue("New day with no limit should be Allowed, got $status", status is TimeLimitStatus.Allowed)
    }

    @Test
    fun midnightRollover_clearsBonusMinutes() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60),
            bonusMinutes = 15,
            bonusDate = LocalDate.of(2026, 2, 18).toString(),
        )
        watchTime.watchSeconds = 60 * 60

        // Bonus active on Wednesday — should be allowed
        assertTrue(manager.canPlay() !is TimeLimitStatus.Blocked)

        // Advance to Thursday
        currentTime = LocalDate.of(2026, 2, 19)
            .atTime(10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        watchTime.watchSeconds = 60 * 60

        store.storedConfig = store.storedConfig!!.copy(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 60, DayOfWeek.THURSDAY to 60),
        )

        // Thursday at limit, Wednesday's bonus should not help
        val status = manager.canPlay()
        assertTrue("Old bonus should not carry over, got $status", status is TimeLimitStatus.Blocked)
    }

    @Test
    fun midnightRollover_dayStartComputedAtQueryTime() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(
                DayOfWeek.WEDNESDAY to 60,
                DayOfWeek.THURSDAY to 120,
            ),
        )
        watchTime.watchSeconds = 55 * 60

        // Wednesday 23:55 — 5 min warning
        currentTime = LocalDate.of(2026, 2, 18)
            .atTime(23, 55)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val warnStatus = manager.canPlay()
        assertTrue(warnStatus is TimeLimitStatus.Warning)

        // Advance past midnight — fresh day
        currentTime = LocalDate.of(2026, 2, 19)
            .atTime(0, 5)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        watchTime.watchSeconds = 0

        val status = manager.canPlay()
        assertTrue("After midnight should be fresh day, got $status", status is TimeLimitStatus.Allowed)
    }

    @Test
    fun setManualLock_true_clearsBonusTime() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 5),
            bonusMinutes = 15,
            bonusDate = LocalDate.of(2026, 2, 18).toString(),
        )

        manager.setManualLock(true)

        val config = store.storedConfig!!
        assertTrue(config.manuallyLocked)
        assertEquals(0, config.bonusMinutes)
    }

    @Test
    fun setManualLock_false_doesNotClearBonus() = runTest {
        store.storedConfig = TimeLimitConfig(
            dailyLimits = mapOf(DayOfWeek.WEDNESDAY to 5),
            bonusMinutes = 15,
            bonusDate = LocalDate.of(2026, 2, 18).toString(),
            manuallyLocked = true,
        )

        manager.setManualLock(false)

        val config = store.storedConfig!!
        assertFalse(config.manuallyLocked)
        // Unlocking doesn't restore bonus — parent must grant new bonus explicitly
        assertEquals(15, config.bonusMinutes)
    }

    // --- Test doubles ---

    class FakeTimeLimitStore : TimeLimitStore {
        var storedConfig: TimeLimitConfig? = null

        override suspend fun getConfig(): TimeLimitConfig? = storedConfig

        override suspend fun saveConfig(config: TimeLimitConfig) {
            storedConfig = config
        }

        override suspend fun updateManualLock(locked: Boolean) {
            storedConfig = (storedConfig ?: TimeLimitConfig()).copy(manuallyLocked = locked)
        }

        override suspend fun updateBonus(minutes: Int, date: String) {
            storedConfig = (storedConfig ?: TimeLimitConfig()).copy(bonusMinutes = minutes, bonusDate = date)
        }
    }

    class FakeWatchTimeProvider : WatchTimeProvider {
        var watchSeconds: Int = 0

        override suspend fun getTodayWatchSeconds(): Int = watchSeconds
    }
}
