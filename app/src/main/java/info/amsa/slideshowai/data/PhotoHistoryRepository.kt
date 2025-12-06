package info.amsa.slideshowai.data

import android.content.Context
import info.amsa.slideshowai.data.database.AppDatabase
import info.amsa.slideshowai.data.database.PhotoHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class PhotoHistoryRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).photoHistoryDao()

    suspend fun getLastShown(file: File): Long {
        return withContext(Dispatchers.IO) {
            dao.getLastShown(file.name) ?: 0L
        }
    }
    
    // Synchronous version for filtering - fetches all history to optimize
    // In a real large app, we might want to do this filtering in the DB query itself
    fun getAllHistorySync(): Map<String, Long> {
        return runBlocking {
            dao.getAllHistory().associate { it.fileName to it.lastShownTimestamp }
        }
    }

    suspend fun updateLastShown(file: File) {
        withContext(Dispatchers.IO) {
            dao.insertHistory(PhotoHistory(file.name, System.currentTimeMillis()))
        }
    }

    fun deleteHistory(fileName: String) {
        dao.deleteHistory(fileName)
    }

    suspend fun clearAllHistory() {
        withContext(Dispatchers.IO) {
            dao.deleteAllHistory()
        }
    }
}
