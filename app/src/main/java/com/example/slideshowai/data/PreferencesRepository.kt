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
        val SERVER_URI_KEY = stringPreferencesKey("server_uri")
    }

    val serverUri: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SERVER_URI_KEY] ?: ""
        }

    suspend fun saveServerUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URI_KEY] = uri
        }
    }
}
