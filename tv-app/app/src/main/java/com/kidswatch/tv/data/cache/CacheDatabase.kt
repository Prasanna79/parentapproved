package com.kidswatch.tv.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kidswatch.tv.data.events.PlayEventDao
import com.kidswatch.tv.data.events.PlayEventEntity

@Database(
    entities = [VideoEntity::class, PlayEventEntity::class, PlaylistEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun videoDao(): PlaylistCacheDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: CacheDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create playlists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        youtube_playlist_id TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        added_at INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'active'
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlists_youtube_playlist_id ON playlists (youtube_playlist_id)")

                // Remove flushed column from play_events by recreating table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS play_events_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        videoId TEXT NOT NULL,
                        playlistId TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        durationSec INTEGER NOT NULL DEFAULT 0,
                        completedPct INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO play_events_new (id, videoId, playlistId, startedAt, durationSec, completedPct) SELECT id, videoId, playlistId, startedAt, durationSec, completedPct FROM play_events")
                db.execSQL("DROP TABLE play_events")
                db.execSQL("ALTER TABLE play_events_new RENAME TO play_events")
            }
        }

        fun getInstance(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "kidswatch_cache"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getInMemoryInstance(context: Context): CacheDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                CacheDatabase::class.java,
            ).allowMainThreadQueries().build()
        }

        internal fun setInstance(db: CacheDatabase) {
            INSTANCE = db
        }
    }
}
