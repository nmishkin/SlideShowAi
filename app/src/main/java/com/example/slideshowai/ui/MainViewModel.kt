package com.example.slideshowai.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshowai.data.PhotoSyncRepository
import com.example.slideshowai.data.PreferencesRepository
import com.example.slideshowai.data.LocationRepository
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    private val photoSyncRepository = PhotoSyncRepository(application)
    private val locationRepository = LocationRepository(application)

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
        
    var localPhotos by mutableStateOf<List<File>>(emptyList())
        private set

    var isInitialized by mutableStateOf(false)
        private set

    init {
        // Load saved config
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesRepository.serverHost,
                preferencesRepository.serverPath,
                preferencesRepository.serverUsername,
                preferencesRepository.quietHoursStart,
                preferencesRepository.quietHoursEnd
            ) { host, path, user, qStart, qEnd ->
                Config(host, path, user, qStart, qEnd)
            }.collect { config ->
                serverHost = config.host
                serverPath = config.path
                serverUsername = config.username
                quietHoursStart = config.quietStart
                quietHoursEnd = config.quietEnd
                
                // Load local photos immediately
                localPhotos = photoSyncRepository.getLocalPhotos()
                isInitialized = true
            }
        }
    }
    
    data class Config(val host: String, val path: String, val username: String, val quietStart: String, val quietEnd: String)
        
    fun updateServerConfig(host: String, path: String, user: String, pass: String, qStart: String, qEnd: String) {
        serverHost = host
        serverPath = path
        serverUsername = user
        serverPassword = pass
        quietHoursStart = qStart
        quietHoursEnd = qEnd
        
        viewModelScope.launch {
            preferencesRepository.saveServerConfig(host, path, user, qStart, qEnd)
        }
    }
    
    fun startSync() {
        viewModelScope.launch {
            statusMessage = "Starting sync..."
            try {
                localPhotos = photoSyncRepository.syncPhotos(serverHost, serverPath, serverUsername, serverPassword) { progress ->
                    Log.d("MainViewModel", "Sync progress: $progress")
                    statusMessage = progress
                }
                statusMessage = "Sync Complete. ${localPhotos.size} photos."
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            }
        }
    }


    suspend fun getLocation(file: File): String? {
        return locationRepository.getLocation(file)
    }
}
