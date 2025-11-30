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

    var serverUri by mutableStateOf("")
        private set
        
    var localPhotos by mutableStateOf<List<File>>(emptyList())
        private set

    init {
        // Load saved URI
        viewModelScope.launch {
            preferencesRepository.serverUri.collect { uri ->
                serverUri = uri
                // Load local photos immediately
                localPhotos = photoSyncRepository.getLocalPhotos()
            }
        }
    }
        
    fun updateServerUri(uri: String) {
        serverUri = uri
        viewModelScope.launch {
            preferencesRepository.saveServerUri(uri)
        }
    }
    
    fun startSync() {
        viewModelScope.launch {
            statusMessage = "Starting sync..."
            try {
                localPhotos = photoSyncRepository.syncPhotos(serverUri) { progress ->
                    statusMessage = progress
                }
                statusMessage = "Sync Complete. ${localPhotos.size} photos."
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            }
        }
    }
}
