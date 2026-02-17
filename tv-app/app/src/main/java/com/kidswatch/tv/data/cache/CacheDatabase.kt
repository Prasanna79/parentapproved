package com.kidswatch.tv.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kidswatch.tv.data.events.PlayEventDao
import com.kidswatch.tv.data.events.PlayEventEntity

@Database(
    entities = [VideoEntity::class, PlayEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun videoDao(): PlaylistCacheDao
    abstract fun playEventDao(): PlayEventDao

    companion object {
        @Volatile
        private var INSTANCE: CacheDatabase? = null

        fun getInstance(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "kidswatch_cache"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
