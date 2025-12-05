package org.amsa.slideshowai.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.amsa.slideshowai.data.PreferencesRepository
import org.amsa.slideshowai.data.LocationRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.amsa.slideshowai.data.PhotoHistoryRepository
import org.amsa.slideshowai.data.TcpCommandServer
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
    
    private fun refreshPhotos() {
        val dir = File(getApplication<Application>().filesDir, "slideshow_photos")
        val allPhotos = dir.listFiles()?.filter { isImageFile(it.name) }?.toList() ?: emptyList()
        localPhotos = allPhotos
        
        // Smart Shuffle Logic
        val historyMap = photoHistoryRepository.getAllHistorySync()
        val threshold = System.currentTimeMillis() - (smartShuffleDays.toLong() * 24 * 60 * 60 * 1000)
        
        val candidates = allPhotos.filter { photo ->
            val lastShown = historyMap[photo.name] ?: 0L
            lastShown < threshold
        }
        
        slideshowPhotos = if (candidates.isNotEmpty()) {
            candidates
        } else {
            // If all photos shown recently, reset/use all (or sort by oldest shown)
            // For now, just use all photos if we ran out of "fresh" ones
            allPhotos
        }
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

    private fun isImageFile(name: String): Boolean {
        val extensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic")
        return extensions.any { name.lowercase().endsWith(it) }
    }
}
