package com.example.slideshowai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {
    
    companion object {
        val SERVER_HOST_KEY = stringPreferencesKey("server_host")
        val SERVER_PATH_KEY = stringPreferencesKey("server_path")
        val SERVER_USERNAME_KEY = stringPreferencesKey("server_username")
        val QUIET_HOURS_START_KEY = stringPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END_KEY = stringPreferencesKey("quiet_hours_end")
        val SMART_SHUFFLE_DAYS_KEY = intPreferencesKey("smart_shuffle_days")
    }

    val serverHost: Flow<String> = context.dataStore.data.map { it[SERVER_HOST_KEY] ?: "" }
    val serverPath: Flow<String> = context.dataStore.data.map { it[SERVER_PATH_KEY] ?: "" }
    val serverUsername: Flow<String> = context.dataStore.data.map { it[SERVER_USERNAME_KEY] ?: "" }
    val quietHoursStart: Flow<String> = context.dataStore.data.map { it[QUIET_HOURS_START_KEY] ?: "22:00" }
    val quietHoursEnd: Flow<String> = context.dataStore.data.map { it[QUIET_HOURS_END_KEY] ?: "07:00" }
    val smartShuffleDays: Flow<Int> = context.dataStore.data.map { it[SMART_SHUFFLE_DAYS_KEY] ?: 30 }

    suspend fun saveServerConfig(host: String, path: String, username: String, quietStart: String, quietEnd: String, shuffleDays: Int) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_HOST_KEY] = host
            preferences[SERVER_PATH_KEY] = path
            preferences[SERVER_USERNAME_KEY] = username
            preferences[QUIET_HOURS_START_KEY] = quietStart
            preferences[QUIET_HOURS_END_KEY] = quietEnd
            preferences[SMART_SHUFFLE_DAYS_KEY] = shuffleDays
        }
    }
}
