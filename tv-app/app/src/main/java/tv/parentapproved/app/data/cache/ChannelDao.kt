package tv.parentapproved.app.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChannelDao {
    @Insert
    suspend fun insert(channel: ChannelEntity): Long

    @Query("SELECT * FROM channels ORDER BY added_at ASC")
    suspend fun getAll(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE source_id = :sourceId")
    suspend fun getBySourceId(sourceId: String): ChannelEntity?

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Query("UPDATE channels SET display_name = :name WHERE id = :id")
    suspend fun updateDisplayName(id: Long, name: String)

    @Query("UPDATE channels SET video_count = :count WHERE id = :id")
    suspend fun updateVideoCount(id: Long, count: Int)

    @Query("UPDATE channels SET display_name = :name, video_count = :count WHERE id = :id")
    suspend fun updateMeta(id: Long, name: String, count: Int)
}
