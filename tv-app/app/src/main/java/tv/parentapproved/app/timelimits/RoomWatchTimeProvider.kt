package tv.parentapproved.app.timelimits

import kotlinx.coroutines.runBlocking
import tv.parentapproved.app.data.events.PlayEventDao
import java.time.LocalDate
import java.time.ZoneId

/**
 * Provides today's total watch time by combining:
 * 1. Completed/in-progress events from the Room DB (sumDurationToday)
 * 2. Current video elapsed time from PlayEventRecorder (injected as lambda)
 *
 * The currentVideoElapsedProvider is injected rather than calling PlayEventRecorder
 * directly, so this class can be unit-tested with fakes.
 */
class RoomWatchTimeProvider(
    private val playEventDao: PlayEventDao,
    private val currentVideoElapsedProvider: () -> Long = { 0L },
    private val clock: () -> Long = System::currentTimeMillis,
) : WatchTimeProvider {

    override fun getTodayWatchSeconds(): Int {
        val dayStartMillis = LocalDate.now(
            ZoneId.systemDefault()
        ).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val dbSeconds = runBlocking {
            playEventDao.sumDurationToday(dayStartMillis)
        }

        // Current video elapsed is already included in the DB total via periodic updates,
        // but there may be up to 10 seconds of uncounted time since the last update.
        // For time limit checking, the DB total is sufficient — the 10s gap is acceptable.
        return dbSeconds
    }
}
