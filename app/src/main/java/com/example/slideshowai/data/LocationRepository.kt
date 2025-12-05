package com.example.slideshowai.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.slideshowai.data.database.AppDatabase
import com.example.slideshowai.data.database.PhotoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class LocationRepository(private val context: Context) {
    private val dao = AppDatabase.getDatabase(context).photoLocationDao()

    suspend fun getLocation(file: File): String? {
        return withContext(Dispatchers.IO) {
            val fileName = file.name
            
            // Check DB
            val cachedLocation = dao.getLocation(fileName)
            if (cachedLocation != null) {
                return@withContext cachedLocation
            }

            // Fetch from EXIF
            val location = fetchLocationFromExif(file)
            if (location != null) {
                dao.insertLocation(PhotoLocation(fileName, location))
            }
            location
        }
    }

    fun deleteLocation(fileName: String) {
        dao.deleteLocation(fileName)
    }

    private fun fetchLocationFromExif(file: File): String? {
        try {
            Log.d("LocationRepository", "Fetching location for file: ${file.name}")
            val exif = ExifInterface(file)
            val latLong = exif.latLong ?: return null
            
            val latitude = latLong[0]
            val longitude = latLong[1]
            val latLongStr = String.format("%.4f, %.4f", latitude, longitude)

            val geocoder = Geocoder(context, Locale.getDefault())
            
            // Basic check for API level, but getFromLocation is available since API 1
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            
            if (addresses.isNullOrEmpty()) {
                return latLongStr
            }

            val address = addresses[0]
            val city = address.locality ?: address.subAdminArea
            val adminArea = address.adminArea
            val countryCode = address.countryCode
            val country = address.countryName

            return if (countryCode == "US") {
                if (adminArea != null) {
                    if (city != null) "$city, $adminArea" else latLongStr
                } else {
                    city ?: country ?: latLongStr
                }
            } else {
                if (country != null) {
                    if (city != null) "$city, $country"
                    else if (adminArea != null) "$adminArea, $country"
                    else country
                } else {
                    latLongStr
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
