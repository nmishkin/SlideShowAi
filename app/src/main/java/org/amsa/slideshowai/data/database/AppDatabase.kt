package org.amsa.slideshowai.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PhotoLocation::class, PhotoHistory::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoLocationDao(): PhotoLocationDao
    abstract fun photoHistoryDao(): PhotoHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "slideshow_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
