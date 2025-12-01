package com.example.slideshowai.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoHistoryDao {
    @Query("SELECT lastShownTimestamp FROM photo_history WHERE fileName = :fileName")
    suspend fun getLastShown(fileName: String): Long?

    // Synchronous version if needed, but Room prefers suspend. 
    // For filtering a list, we might want to fetch all history at once.
    @Query("SELECT * FROM photo_history")
    suspend fun getAllHistory(): List<PhotoHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(photoHistory: PhotoHistory)
}
