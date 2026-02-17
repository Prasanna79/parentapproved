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

    @Query("SELECT * FROM play_events WHERE flushed = 0")
    suspend fun getUnflushed(): List<PlayEventEntity>

    @Query("UPDATE play_events SET flushed = 1 WHERE id IN (:ids)")
    suspend fun markFlushed(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM play_events WHERE flushed = 0")
    suspend fun unflushedCount(): Int

    @Query("DELETE FROM play_events WHERE flushed = 1")
    suspend fun deleteFlushed()

    @Query("DELETE FROM play_events")
    suspend fun deleteAll()
}
