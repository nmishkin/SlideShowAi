package com.example.slideshowai.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

class LocationRepository(private val context: Context) {
    private val cacheFile = File(context.filesDir, "location_cache.json")
    private val cache = mutableMapOf<String, String>()

    init {
        loadCache()
    }

    private fun loadCache() {
        if (cacheFile.exists()) {
            try {
                val jsonString = cacheFile.readText()
                val jsonObject = JSONObject(jsonString)
                jsonObject.keys().forEach { key ->
                    cache[key] = jsonObject.getString(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveCache() {
        try {
            val jsonObject = JSONObject(cache as Map<*, *>)
            cacheFile.writeText(jsonObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getLocation(file: File): String? {
        return withContext(Dispatchers.IO) {
            val fileName = file.name
            if (cache.containsKey(fileName)) {
                return@withContext cache[fileName]
            }

            val location = fetchLocationFromExif(file)
            if (location != null) {
                cache[fileName] = location
                saveCache()
            }
            location
        }
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
