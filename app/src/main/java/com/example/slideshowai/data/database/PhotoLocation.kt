package com.example.slideshowai.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_locations")
data class PhotoLocation(
    @PrimaryKey val fileName: String,
    val location: String
)
