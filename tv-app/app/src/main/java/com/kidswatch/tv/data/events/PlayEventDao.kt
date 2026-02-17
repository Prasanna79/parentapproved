package com.kidswatch.tv.data.events

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity): Long

    @Update
    suspend fun update(event: PlayEventEntity)

    @Query("SELECT * FROM play_events ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<PlayEventEntity>

    @Query("SELECT * FROM play_events WHERE startedAt >= :dayStartMillis ORDER BY startedAt DESC")
    suspend fun getForToday(dayStartMillis: Long): List<PlayEventEntity>

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM play_events WHERE startedAt >= :dayStartMillis")
    suspend fun sumDurationToday(dayStartMillis: Long): Int

    @Query("SELECT * FROM play_events WHERE id = :id")
    suspend fun getById(id: Long): PlayEventEntity?

    @Query("SELECT COUNT(*) FROM play_events")
    suspend fun count(): Int

    @Query("DELETE FROM play_events")
    suspend fun deleteAll()
}
