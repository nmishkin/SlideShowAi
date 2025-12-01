package com.example.slideshowai.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class PhotoHistoryRepository(context: Context) {
    private val historyFile = File(context.filesDir, "photo_history.json")
    private val history = mutableMapOf<String, Long>()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        if (historyFile.exists()) {
            try {
                val jsonString = historyFile.readText()
                val jsonObject = JSONObject(jsonString)
                jsonObject.keys().forEach { key ->
                    history[key] = jsonObject.getLong(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveHistory() {
        try {
            val jsonObject = JSONObject(history as Map<*, *>)
            historyFile.writeText(jsonObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getLastShown(file: File): Long {
        return withContext(Dispatchers.IO) {
            history[file.name] ?: 0L
        }
    }
    
    // Synchronous version for filtering
    fun getLastShownSync(file: File): Long {
        return history[file.name] ?: 0L
    }

    suspend fun updateLastShown(file: File) {
        withContext(Dispatchers.IO) {
            history[file.name] = System.currentTimeMillis()
            saveHistory()
        }
    }
}
