package tv.parentapproved.app.timelimits

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.parentapproved.app.data.cache.TimeLimitConfigEntity
import tv.parentapproved.app.data.cache.TimeLimitDao

class TimeLimitDaoTest {

    private lateinit var dao: FakeTimeLimitDao

    @Before
    fun setup() {
        dao = FakeTimeLimitDao()
    }

    @Test
    fun getConfig_empty_returnsNull() = runBlocking {
        assertNull(dao.getConfig())
    }

    @Test
    fun insertOrUpdate_newConfig_persists() = runBlocking {
        val entity = TimeLimitConfigEntity(mondayLimitMin = 60)
        dao.insertOrUpdate(entity)
        val result = dao.getConfig()
        assertNotNull(result)
        assertEquals(60, result!!.mondayLimitMin)
    }

    @Test
    fun insertOrUpdate_existingConfig_updates() = runBlocking {
        dao.insertOrUpdate(TimeLimitConfigEntity(mondayLimitMin = 60))
        dao.insertOrUpdate(TimeLimitConfigEntity(mondayLimitMin = 120))
        val result = dao.getConfig()
        assertEquals(120, result!!.mondayLimitMin)
    }

    @Test
    fun getConfig_returnsLatest() = runBlocking {
        dao.insertOrUpdate(TimeLimitConfigEntity(saturdayLimitMin = 90))
        dao.insertOrUpdate(TimeLimitConfigEntity(saturdayLimitMin = 180))
        assertEquals(180, dao.getConfig()!!.saturdayLimitMin)
    }

    @Test
    fun setManualLock_persistsFlag() = runBlocking {
        dao.insertOrUpdate(TimeLimitConfigEntity())
        dao.setManualLock(true)
        assertTrue(dao.getConfig()!!.manuallyLocked)
        dao.setManualLock(false)
        assertFalse(dao.getConfig()!!.manuallyLocked)
    }

    @Test
    fun updateBonus_setsMinutesAndDate() = runBlocking {
        dao.insertOrUpdate(TimeLimitConfigEntity())
        dao.updateBonus(15, "2026-02-18")
        val result = dao.getConfig()!!
        assertEquals(15, result.bonusMinutes)
        assertEquals("2026-02-18", result.bonusDate)
    }

    @Test
    fun updateBonus_accumulates() = runBlocking {
        dao.insertOrUpdate(TimeLimitConfigEntity())
        dao.updateBonus(15, "2026-02-18")
        dao.updateBonus(30, "2026-02-18") // caller is responsible for accumulation
        assertEquals(30, dao.getConfig()!!.bonusMinutes)
    }
}

/** In-memory fake of TimeLimitDao for unit tests. */
class FakeTimeLimitDao : TimeLimitDao {
    private var stored: TimeLimitConfigEntity? = null

    override suspend fun getConfig(): TimeLimitConfigEntity? = stored

    override suspend fun insertOrUpdate(config: TimeLimitConfigEntity) {
        stored = config
    }

    override suspend fun setManualLock(locked: Boolean) {
        stored = stored?.copy(manuallyLocked = locked)
    }

    override suspend fun updateBonus(minutes: Int, date: String) {
        stored = stored?.copy(bonusMinutes = minutes, bonusDate = date)
    }
}
