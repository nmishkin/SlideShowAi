package info.amsa.slideshowai.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import info.amsa.slideshowai.data.database.AppDatabase
import info.amsa.slideshowai.data.database.PhotoLocation
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
            var latLong = exif.latLong
            
            if (latLong == null) {
                // Fallback: Try to read raw attributes and debug
                val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
                
                Log.d("LocationRepository", "Standard latLong null. Raw: Lat=$lat Ref=$latRef, Lon=$lon Ref=$lonRef")
                
                if (lat != null && lon != null) {
                    // Start of rudimentary parser if needed, or simply return null after logging for now
                    // If we want to fix it, we'd need to parse "d/1, m/1, s/1000" style strings
                    latLong = parseLatLong(lat, latRef, lon, lonRef)
                }
            }

            val validLatLong = latLong ?: return null
            
            val latitude = validLatLong[0]
            val longitude = validLatLong[1]
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
    suspend fun getAllLocations(): List<PhotoLocation> {
        return dao.getAllLocations()
    }

    suspend fun clearAllLocations() {
        dao.deleteAllLocations()
    }
    
    // Helper to parse GPS "degrees/1, minutes/1, seconds/1" string format manually
    private fun parseLatLong(lat: String, latRef: String?, lon: String, lonRef: String?): DoubleArray? {
        try {
            val latVal = convertRationalLatLonToDouble(lat, latRef)
            val lonVal = convertRationalLatLonToDouble(lon, lonRef)
            return doubleArrayOf(latVal, lonVal)
        } catch (e: Exception) {
            Log.e("LocationRepository", "Manual GPS parsing failed", e)
            return null
        }
    }

    private fun convertRationalLatLonToDouble(rationalString: String, ref: String?): Double {
        val parts = rationalString.split(",", " ").filter { it.isNotBlank() }
        
        var degrees = 0.0
        var minutes = 0.0
        var seconds = 0.0
        
        if (parts.isNotEmpty()) degrees = parseRational(parts[0])
        if (parts.size > 1) minutes = parseRational(parts[1])
        if (parts.size > 2) seconds = parseRational(parts[2])
        
        var result = degrees + (minutes / 60.0) + (seconds / 3600.0)
        
        if (ref == "S" || ref == "W") {
            result = -result
        }
        
        return result
    }

    private fun parseRational(rat: String): Double {
        val parts = rat.split("/")
        if (parts.size == 2) {
            return parts[0].toDouble() / parts[1].toDouble()
        }
        return parts[0].toDouble()
    }
}
