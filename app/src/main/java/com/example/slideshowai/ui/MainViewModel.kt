package com.example.slideshowai.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.slideshowai.data.PhotoSyncRepository
import com.example.slideshowai.data.PreferencesRepository
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    private val photoSyncRepository = PhotoSyncRepository(application)

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
                preferencesRepository.serverUsername
            ) { host, path, user ->
                Triple(host, path, user)
            }.collect { (host, path, user) ->
                serverHost = host
                serverPath = path
                serverUsername = user
                
                // Load local photos immediately
                localPhotos = photoSyncRepository.getLocalPhotos()
                isInitialized = true
            }
        }
    }
        
    fun updateServerConfig(host: String, path: String, user: String, pass: String) {
        serverHost = host
        serverPath = path
        serverUsername = user
        serverPassword = pass
        
        viewModelScope.launch {
            preferencesRepository.saveServerConfig(host, path, user)
        }
    }
    
    fun startSync() {
        viewModelScope.launch {
            statusMessage = "Starting sync..."
            try {
                localPhotos = photoSyncRepository.syncPhotos(serverHost, serverPath, serverUsername, serverPassword) { progress ->
                    statusMessage = progress
                }
                statusMessage = "Sync Complete. ${localPhotos.size} photos."
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            }
        }
    }
}
