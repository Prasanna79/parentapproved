package tv.parentapproved.app.data

import tv.parentapproved.app.data.events.PlayEventDao
import tv.parentapproved.app.data.events.PlayEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayEventDaoExtendedTest {

    private lateinit var dao: FakePlayEventDao

    @Before
    fun setup() {
        dao = FakePlayEventDao()
    }

    @Test
    fun getRecentEvents_returnsLast20() = runTest {
        repeat(25) { i ->
            dao.insert(PlayEventEntity(
                videoId = "v$i", playlistId = "PL1",
                startedAt = 1000L + i, durationSec = 60
            ))
        }
        val recent = dao.getRecent(20)
        assertEquals(20, recent.size)
        // Most recent first
        assertTrue(recent[0].startedAt > recent[19].startedAt)
    }

    @Test
    fun getEventsForToday_filtersCorrectly() = runTest {
        val todayStart = 86400000L // arbitrary "today start"
        // Yesterday events
        dao.insert(PlayEventEntity(videoId = "v1", playlistId = "PL1", startedAt = todayStart - 1000))
        dao.insert(PlayEventEntity(videoId = "v2", playlistId = "PL1", startedAt = todayStart - 5000))
        // Today events
        dao.insert(PlayEventEntity(videoId = "v3", playlistId = "PL1", startedAt = todayStart + 1000))
        dao.insert(PlayEventEntity(videoId = "v4", playlistId = "PL1", startedAt = todayStart + 5000))

        val todayEvents = dao.getForToday(todayStart)
        assertEquals(2, todayEvents.size)
    }

    @Test
    fun getTotalWatchTimeToday_sumsCorrectly() = runTest {
        val todayStart = 86400000L
        dao.insert(PlayEventEntity(videoId = "v1", playlistId = "PL1", startedAt = todayStart + 100, durationSec = 120))
        dao.insert(PlayEventEntity(videoId = "v2", playlistId = "PL1", startedAt = todayStart + 200, durationSec = 180))
        dao.insert(PlayEventEntity(videoId = "v3", playlistId = "PL1", startedAt = todayStart - 100, durationSec = 999)) // yesterday

        val total = dao.sumDurationToday(todayStart)
        assertEquals(300, total)
    }
}

class FakePlayEventDao : PlayEventDao {
    private val store = mutableListOf<PlayEventEntity>()
    private var nextId = 1L

    override suspend fun insert(event: PlayEventEntity): Long {
        val entity = event.copy(id = nextId++)
        store.add(entity)
        return entity.id
    }

    override suspend fun update(event: PlayEventEntity) {
        val idx = store.indexOfFirst { it.id == event.id }
        if (idx >= 0) store[idx] = event
    }

    override suspend fun getRecent(limit: Int): List<PlayEventEntity> =
        store.sortedByDescending { it.startedAt }.take(limit)

    override suspend fun getForToday(dayStartMillis: Long): List<PlayEventEntity> =
        store.filter { it.startedAt >= dayStartMillis }.sortedByDescending { it.startedAt }

    override suspend fun sumDurationToday(dayStartMillis: Long): Int =
        store.filter { it.startedAt >= dayStartMillis }.sumOf { it.durationSec }

    override suspend fun getById(id: Long): PlayEventEntity? =
        store.find { it.id == id }

    override suspend fun count(): Int = store.size

    override suspend fun deleteAll() {
        store.clear()
    }
}
