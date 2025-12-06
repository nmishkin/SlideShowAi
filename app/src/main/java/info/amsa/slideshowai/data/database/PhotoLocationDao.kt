package info.amsa.slideshowai.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoLocationDao {
    @Query("SELECT location FROM photo_locations WHERE fileName = :fileName")
    suspend fun getLocation(fileName: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(photoLocation: PhotoLocation)

    @Query("DELETE FROM photo_locations WHERE fileName = :fileName")
    fun deleteLocation(fileName: String)

    @Query("SELECT * FROM photo_locations")
    suspend fun getAllLocations(): List<PhotoLocation>

    @Query("DELETE FROM photo_locations")
    suspend fun deleteAllLocations()
}
