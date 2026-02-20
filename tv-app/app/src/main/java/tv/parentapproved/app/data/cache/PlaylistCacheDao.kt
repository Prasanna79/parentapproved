package tv.parentapproved.app.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaylistCacheDao {
    @Query("SELECT * FROM videos WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getByPlaylist(playlistId: String): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("DELETE FROM videos")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun count(): Int
}
