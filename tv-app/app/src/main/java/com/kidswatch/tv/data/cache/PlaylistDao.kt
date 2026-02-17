package com.kidswatch.tv.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY added_at ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE youtube_playlist_id = :youtubeId")
    suspend fun getByYoutubeId(youtubeId: String): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()

    @Query("UPDATE playlists SET display_name = :name WHERE id = :id")
    suspend fun updateDisplayName(id: Long, name: String)
}
