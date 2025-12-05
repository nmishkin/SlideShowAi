package info.amsa.slideshowai.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_history")
data class PhotoHistory(
    @PrimaryKey val fileName: String,
    val lastShownTimestamp: Long
)
