package org.amsa.slideshowai.data

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
        val QUIET_HOURS_START_KEY = stringPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END_KEY = stringPreferencesKey("quiet_hours_end")
        val SMART_SHUFFLE_DAYS_KEY = intPreferencesKey("smart_shuffle_days")
        val PHOTO_DURATION_KEY = stringPreferencesKey("photo_duration")
    }

    val quietHoursStart: Flow<String> = context.dataStore.data.map { it[QUIET_HOURS_START_KEY] ?: "22:00" }
    val quietHoursEnd: Flow<String> = context.dataStore.data.map { it[QUIET_HOURS_END_KEY] ?: "07:00" }
    val smartShuffleDays: Flow<Int> = context.dataStore.data.map { it[SMART_SHUFFLE_DAYS_KEY] ?: 30 }
    val photoDuration: Flow<String> = context.dataStore.data.map { it[PHOTO_DURATION_KEY] ?: "00:00:05" }

    suspend fun saveServerConfig(quietStart: String, quietEnd: String, shuffleDays: Int, duration: String) {
        context.dataStore.edit { preferences ->
            preferences[QUIET_HOURS_START_KEY] = quietStart
            preferences[QUIET_HOURS_END_KEY] = quietEnd
            preferences[SMART_SHUFFLE_DAYS_KEY] = shuffleDays
            preferences[PHOTO_DURATION_KEY] = duration
        }
    }
}
