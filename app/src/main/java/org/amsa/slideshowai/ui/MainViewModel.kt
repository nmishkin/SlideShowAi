package org.amsa.slideshowai.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.amsa.slideshowai.data.PhotoSyncRepository
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
    private val photoSyncRepository = PhotoSyncRepository(application)
    private val locationRepository = LocationRepository(application)
    private val photoHistoryRepository = PhotoHistoryRepository(application)

    var statusMessage by mutableStateOf("Ready")
        private set

    var serverHost by mutableStateOf("")
        private set
    var serverPath by mutableStateOf("")
        private set
    var serverUsername by mutableStateOf("")
        private set
    var serverPassword by mutableStateOf("")
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
            tcpServer.start(4000) { cmd, args ->
                handleTcpCommand(cmd, args)
            }
        }

        // Load saved config
        viewModelScope.launch {
            val serverConfigFlow = kotlinx.coroutines.flow.combine(
                preferencesRepository.serverHost,
                preferencesRepository.serverPath,
                preferencesRepository.serverUsername
            ) { host, path, user ->
                Triple(host, path, user)
            }

            kotlinx.coroutines.flow.combine(
                serverConfigFlow,
                preferencesRepository.quietHoursStart,
                preferencesRepository.quietHoursEnd,
                preferencesRepository.smartShuffleDays,
                preferencesRepository.photoDuration
            ) { (host, path, user), qStart, qEnd, days, duration ->
                Config(host, path, user, qStart, qEnd, days, duration)
            }.first().let { config ->
                serverHost = config.host
                serverPath = config.path
                serverUsername = config.username
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
    
    private suspend fun handleTcpCommand(cmd: String, args: List<String>): String {
        return if (cmd == "sync") {
            if (serverPassword.isBlank()) {
                JSONObject().apply {
                    put("status", "error")
                    put("message", "Password not set in app")
                }.toString()
            } else {
                try {
                    // Trigger sync
                    // We need to run sync and wait for result
                    val result = photoSyncRepository.syncPhotos(serverHost, serverPath, serverUsername, serverPassword) { progress ->
                        Log.d("MainViewModel", "TCP Sync progress: $progress")
                        // Optional: broadcast progress? For now just log.
                        viewModelScope.launch {
                            statusMessage = progress
                        }
                    }
                    
                    viewModelScope.launch {
                        statusMessage = "Sync Complete. ${result.size} photos."
                        refreshPhotos()
                    }

                    JSONObject().apply {
                        put("status", "success")
                        put("message", "Sync complete")
                        put("synced_count", result.size)
                    }.toString()
                } catch (e: Exception) {
                     viewModelScope.launch {
                        statusMessage = "TCP Sync Error: ${e.message}"
                        syncErrorMessage = e.message
                    }
                    JSONObject().apply {
                        put("status", "error")
                        put("message", e.message ?: "Unknown sync error")
                    }.toString()
                }
            }
        } else {
            JSONObject().apply {
                put("status", "error")
                put("message", "Unknown command: $cmd")
            }.toString()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tcpServer.stop()
    }
    
    private fun refreshPhotos() {
        val allPhotos = photoSyncRepository.getLocalPhotos()
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
    
    data class Config(val host: String, val path: String, val username: String, val quietStart: String, val quietEnd: String, val shuffleDays: Int, val duration: String)
        
    fun updateServerConfig(host: String, path: String, user: String, pass: String, qStart: String, qEnd: String, shuffleDays: String, duration: String) {
        serverHost = host
        serverPath = path
        serverUsername = user
        serverPassword = pass
        quietHoursStart = qStart
        quietHoursEnd = qEnd
        photoDuration = duration
        
        val days = shuffleDays.toIntOrNull() ?: 30
        smartShuffleDays = days
        
        viewModelScope.launch {
            preferencesRepository.saveServerConfig(host, path, user, qStart, qEnd, days, duration)
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

    fun startSync() {
        viewModelScope.launch {
            statusMessage = "Starting sync..."
            try {
                val syncedPhotos = photoSyncRepository.syncPhotos(serverHost, serverPath, serverUsername, serverPassword) { progress ->
                    Log.d("MainViewModel", "Sync progress: $progress")
                    statusMessage = progress
                }
                statusMessage = "Sync Complete. ${syncedPhotos.size} photos."
                refreshPhotos()
            } catch (e: Exception) {
                statusMessage = "Error occurred during sync."
                syncErrorMessage = e.message ?: "Unknown error"
            }
        }
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
