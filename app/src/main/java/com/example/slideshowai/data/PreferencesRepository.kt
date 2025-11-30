package com.example.slideshowai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {
    
    companion object {
        val SERVER_HOST_KEY = stringPreferencesKey("server_host")
        val SERVER_PATH_KEY = stringPreferencesKey("server_path")
        val SERVER_USERNAME_KEY = stringPreferencesKey("server_username")
    }

    val serverHost: Flow<String> = context.dataStore.data.map { it[SERVER_HOST_KEY] ?: "" }
    val serverPath: Flow<String> = context.dataStore.data.map { it[SERVER_PATH_KEY] ?: "" }
    val serverUsername: Flow<String> = context.dataStore.data.map { it[SERVER_USERNAME_KEY] ?: "" }

    suspend fun saveServerConfig(host: String, path: String, username: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_HOST_KEY] = host
            preferences[SERVER_PATH_KEY] = path
            preferences[SERVER_USERNAME_KEY] = username
        }
    }
}
