package info.amsa.slideshowai.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import info.amsa.slideshowai.data.PreferencesRepository
import info.amsa.slideshowai.data.LocationRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import info.amsa.slideshowai.data.PhotoHistoryRepository
import info.amsa.slideshowai.data.TcpCommandServer
import org.json.JSONObject
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    private val locationRepository = LocationRepository(application)
    private val photoHistoryRepository = PhotoHistoryRepository(application)


    var statusMessage by mutableStateOf("Ready")
        private set


        
    var quietHoursStart by mutableStateOf("22:00")
        private set
    var quietHoursEnd by mutableStateOf("07:00")
        private set
        
    var smartShuffleDays by mutableIntStateOf(30)
        private set
        
    var photoDuration by mutableStateOf("00:00:05")
        private set
        
    var localPhotos by mutableStateOf<List<File>>(emptyList())
        private set
        
    var slideshowPhotos by mutableStateOf<List<File>>(emptyList())
        private set

    var isInitialized by mutableStateOf(false)
        private set

    private val tcpServer = TcpCommandServer()

    init {
        // Start TCP Server
        viewModelScope.launch {
            tcpServer.start(4000) { cmd, json, socket ->
                handleTcpCommand(cmd, json, socket)
            }
        }

        // Load saved config
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesRepository.quietHoursStart,
                preferencesRepository.quietHoursEnd,
                preferencesRepository.smartShuffleDays,
                preferencesRepository.photoDuration
            ) { qStart, qEnd, days, duration ->
                Config(qStart, qEnd, days, duration)
            }.first().let { config ->
                quietHoursStart = config.quietStart
                quietHoursEnd = config.quietEnd
                smartShuffleDays = config.shuffleDays
                photoDuration = config.duration
                
                // Load local photos immediately
                refreshPhotos()
                isInitialized = true
            }
        }
    }
    
    private suspend fun handleTcpCommand(cmd: String, json: JSONObject, socket: java.net.Socket) {
        val writer = java.io.PrintWriter(socket.getOutputStream(), true)
        
        try {
            when (cmd) {
                "list_files" -> {
                    val files = localPhotos.map { it.name }
                    val response = JSONObject().apply {
                        put("status", "ok")
                        put("files", org.json.JSONArray(files))
                    }
                    writer.println(response.toString())
                }
                "delete_file" -> {
                    val filename = json.getString("name")
                    val file = File(getApplication<Application>().filesDir, "slideshow_photos/$filename")
                    
                    if (file.exists()) {
                        if (file.delete()) {
                            // Clean up DB
                            try {
                                locationRepository.deleteLocation(filename)
                                photoHistoryRepository.deleteHistory(filename)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            refreshPhotos()
                            writer.println(JSONObject().put("status", "ok").put("message", "Deleted $filename").toString())
                        } else {
                            writer.println(JSONObject().put("status", "error").put("message", "Failed to delete $filename").toString())
                        }
                    } else {
                         writer.println(JSONObject().put("status", "ok").put("message", "File not found (already deleted)").toString())
                    }
                }
                "receive_file" -> {
                    val filename = json.getString("name")
                    val size = json.getLong("size")
                    
                    writer.println(JSONObject().put("status", "ready").toString())
                    
                    // Read binary data
                    val dir = File(getApplication<Application>().filesDir, "slideshow_photos")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, filename)
                    
                    val inputStream = socket.getInputStream()
                    val outputStream = java.io.FileOutputStream(file)
                    val buffer = ByteArray(4096)
                    var bytesRead: Long = 0
                    
                    while (bytesRead < size) {
                        val remaining = size - bytesRead
                        val readSize = if (remaining < buffer.size) remaining.toInt() else buffer.size
                        val count = inputStream.read(buffer, 0, readSize)
                        if (count == -1) break
                        outputStream.write(buffer, 0, count)
                        bytesRead += count
                    }
                    outputStream.close()
                    
                    if (bytesRead == size) {
                         refreshPhotos()
                         writer.println(JSONObject().put("status", "ok").put("message", "File received").toString())
                    } else {
                         writer.println(JSONObject().put("status", "error").put("message", "Incomplete transfer").toString())
                    }
                }
                "get_db" -> {
                    val dbType = json.getString("db")
                    if (dbType == "location") {
                        val locations = locationRepository.getAllLocations()
                        val jsonArray = org.json.JSONArray()
                        locations.forEach { 
                            jsonArray.put(JSONObject().put("fileName", it.fileName).put("location", it.location))
                        }
                        writer.println(JSONObject().put("status", "ok").put("data", jsonArray).toString())
                    } else if (dbType == "history") {
                        val history = photoHistoryRepository.getAllHistorySync()
                        val jsonArray = org.json.JSONArray()
                        history.forEach { (name, time) ->
                             jsonArray.put(JSONObject().put("fileName", name).put("lastShown", time))
                        }
                         writer.println(JSONObject().put("status", "ok").put("data", jsonArray).toString())
                    } else {
                        writer.println(JSONObject().put("status", "error").put("message", "Unknown db: $dbType").toString())
                    }
                }
                "clear_db" -> {
                    val dbType = json.getString("db")
                    if (dbType == "location") {
                        locationRepository.clearAllLocations()
                        writer.println(JSONObject().put("status", "ok").put("message", "Location DB cleared").toString())
                    } else if (dbType == "history") {
                        photoHistoryRepository.clearAllHistory()
                         refreshPhotos() // Re-shuffle might depend on history
                        writer.println(JSONObject().put("status", "ok").put("message", "History DB cleared").toString())
                    } else {
                        writer.println(JSONObject().put("status", "error").put("message", "Unknown db: $dbType").toString())
                    }
                }
                "delete_all_files" -> {
                    val dir = File(getApplication<Application>().filesDir, "slideshow_photos")
                    if (dir.exists()) {
                        dir.listFiles()?.forEach { it.delete() }
                    }
                    
                    // Clear DBs as well
                    locationRepository.clearAllLocations()
                    photoHistoryRepository.clearAllHistory()
                    refreshPhotos()
                    
                    writer.println(JSONObject().put("status", "ok").put("message", "All photos and data deleted").toString())
                }
                "get_device_info" -> {
                    val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
                    val width = displayMetrics.widthPixels
                    val height = displayMetrics.heightPixels
                    writer.println(JSONObject().put("status", "ok").put("width", width).put("height", height).toString())
                }
                else -> {
                    writer.println(JSONObject().put("status", "error").put("message", "Unknown command: $cmd").toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
             writer.println(JSONObject().put("status", "error").put("message", e.message).toString())
        }
    }

    override fun onCleared() {
        super.onCleared()
        tcpServer.stop()
    }
    
    private var isLandscape by mutableStateOf(true)

    fun updateOrientation(landscape: Boolean) {
        if (isLandscape != landscape) {
            isLandscape = landscape
            refreshPhotos()
        }
    }

    private fun refreshPhotos() {
        val dir = File(getApplication<Application>().filesDir, "slideshow_photos")
        val allPhotos = dir.listFiles()?.filter { isImageFile(it.name) }?.toList() ?: emptyList()
        localPhotos = allPhotos
        
        // Filter by Orientation
        val orientedPhotos = allPhotos.filter { file ->
            isPhotoMatchingOrientation(file, isLandscape)
        }

        // Smart Shuffle Logic
        val historyMap = photoHistoryRepository.getAllHistorySync()
        val threshold = System.currentTimeMillis() - (smartShuffleDays.toLong() * 24 * 60 * 60 * 1000)
        
        val candidates = orientedPhotos.filter { photo ->
            val lastShown = historyMap[photo.name] ?: 0L
            lastShown < threshold
        }
        
        slideshowPhotos = if (candidates.isNotEmpty()) {
            candidates.shuffled()
        } else {
            // If all photos shown recently, reset/use all matching orientation
            orientedPhotos.shuffled()
        }
    }

    private fun isPhotoMatchingOrientation(file: File, targetLandscape: Boolean): Boolean {
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(file)
            val rotation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
            
            var width = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0)
            var height = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0)
            
            if (width == 0 || height == 0) {
                 val options = android.graphics.BitmapFactory.Options()
                 options.inJustDecodeBounds = true
                 android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                 width = options.outWidth
                 height = options.outHeight
            }

            if (width == 0 || height == 0) return true // Default to include if unknown
            
            val isRotated = rotation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 || 
                            rotation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270
                            
            val finalWidth = if (isRotated) height else width
            val finalHeight = if (isRotated) width else height
            
            val isPhotoLandscape = finalWidth >= finalHeight
            
            isPhotoLandscape == targetLandscape
        } catch (e: Exception) {
            true // Default to include on error
        }
    }

    private fun isImageFile(name: String): Boolean {
        val extensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic")
        return extensions.any { name.lowercase().endsWith(it) }
    }

    data class Config(val quietStart: String, val quietEnd: String, val shuffleDays: Int, val duration: String)
        
    fun updateServerConfig(qStart: String, qEnd: String, shuffleDays: String, duration: String) {
        quietHoursStart = qStart
        quietHoursEnd = qEnd
        photoDuration = duration
        
        val days = shuffleDays.toIntOrNull() ?: 30
        smartShuffleDays = days
        
        viewModelScope.launch {
            preferencesRepository.saveServerConfig(qStart, qEnd, days, duration)
        }
    }
    
    fun getPhotoDurationMillis(): Long {
        return try {
            val parts = photoDuration.split(":")
            val hours = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val minutes = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val seconds = parts.getOrNull(2)?.toLongOrNull() ?: 5L
            
            ((hours * 3600) + (minutes * 60) + seconds) * 1000L
        } catch (e: Exception) {
            5000L
        }
    }
    
    var syncErrorMessage by mutableStateOf<String?>(null)
        private set

    fun clearSyncError() {
        syncErrorMessage = null
    }

    suspend fun getLocation(file: File): String? {
        return locationRepository.getLocation(file)
    }
    
    fun markPhotoAsShown(file: File) {
        viewModelScope.launch {
            photoHistoryRepository.updateLastShown(file)
        }
    }
}
