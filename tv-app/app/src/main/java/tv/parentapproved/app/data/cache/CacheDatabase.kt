package tv.parentapproved.app.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tv.parentapproved.app.data.events.PlayEventDao
import tv.parentapproved.app.data.events.PlayEventEntity

@Database(
    entities = [VideoEntity::class, PlayEventEntity::class, ChannelEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun videoDao(): PlaylistCacheDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun channelDao(): ChannelDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create channels table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS channels (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        source_type TEXT NOT NULL,
                        source_id TEXT NOT NULL,
                        source_url TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        video_count INTEGER NOT NULL DEFAULT 0,
                        added_at INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'active'
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_channels_source_id ON channels (source_id)")

                // 2. Migrate playlists → channels
                db.execSQL("""
                    INSERT INTO channels (source_type, source_id, source_url, display_name, added_at, status)
                    SELECT 'yt_playlist', youtube_playlist_id,
                           'https://www.youtube.com/playlist?list=' || youtube_playlist_id,
                           display_name, added_at, status
                    FROM playlists
                """.trimIndent())

                // 3. Update video counts from cached videos
                db.execSQL("""
                    UPDATE channels SET video_count = (
                        SELECT COUNT(*) FROM videos WHERE videos.playlistId = channels.source_id
                    )
                """.trimIndent())

                // 4. Add title column to play_events
                db.execSQL("ALTER TABLE play_events ADD COLUMN title TEXT NOT NULL DEFAULT ''")

                // 5. Backfill titles from videos table
                db.execSQL("""
                    UPDATE play_events SET title = COALESCE(
                        (SELECT videos.title FROM videos WHERE videos.videoId = play_events.videoId LIMIT 1),
                        ''
                    ) WHERE title = ''
                """.trimIndent())

                // 6. Drop old playlists table
                db.execSQL("DROP TABLE IF EXISTS playlists")
            }
        }

        fun getInstance(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "parentapproved_cache"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
