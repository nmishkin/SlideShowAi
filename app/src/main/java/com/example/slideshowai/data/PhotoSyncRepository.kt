package com.example.slideshowai.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileOutputStream

class PhotoSyncRepository(private val context: Context) {

    private val localDirectory: File = File(context.filesDir, "slideshow_photos")

    init {
        if (!localDirectory.exists()) {
            localDirectory.mkdirs()
        }
    }

    suspend fun syncPhotos(host: String, path: String, user: String, pass: String, onProgress: (String) -> Unit): List<File> {
        return withContext(Dispatchers.IO) {
            if (host.isBlank()) return@withContext getLocalPhotos()

            try {
                // Assume FTP for now as per requirements
                syncFtp(host, path, user, pass, onProgress)
            } catch (e: Exception) {
                onProgress("Sync failed: ${e.message}")
                e.printStackTrace()
            }
            
            getLocalPhotos()
        }
    }

    private fun syncFtp(host: String, path: String, user: String, pass: String, onProgress: (String) -> Unit) {
        val ftp = FTPClient()
        try {
            val port = 21 // Default FTP port
            val username = if (user.isBlank()) "anonymous" else user
            val password = if (pass.isBlank()) "" else pass
            val remotePath = if (path.isBlank()) "/" else path

            onProgress("Connecting to $host...")
            ftp.connect(host, port)
            ftp.login(username, password)
            ftp.enterLocalPassiveMode()
            ftp.setFileType(FTP.BINARY_FILE_TYPE)

            onProgress("Listing files...")
            // Change to directory if path is specified
            if (remotePath != "/") {
                ftp.changeWorkingDirectory(remotePath)
            }

            val remoteFiles = ftp.listFiles().filter { it.isFile && isImageFile(it.name) }
            val remoteFileNames = remoteFiles.map { it.name }.toSet()
            
            // 1. Delete local files not on server
            val localFiles = getLocalPhotos()
            localFiles.forEach { localFile ->
                if (!remoteFileNames.contains(localFile.name)) {
                    onProgress("Deleting ${localFile.name}...")
                    localFile.delete()
                }
            }

            // 2. Download new files
            remoteFiles.forEach { remoteFile ->
                val localFile = File(localDirectory, remoteFile.name)
                if (!localFile.exists() || localFile.length() != remoteFile.size) {
                    onProgress("Downloading ${remoteFile.name}...")
                    val outputStream = FileOutputStream(localFile)
                    ftp.retrieveFile(remoteFile.name, outputStream)
                    outputStream.close()
                }
            }
            
            onProgress("Sync Complete")

        } catch (e: Exception) {
            throw e
        } finally {
            if (ftp.isConnected) {
                ftp.logout()
                ftp.disconnect()
            }
        }
    }



    fun getLocalPhotos(): List<File> {
        return localDirectory.listFiles()?.filter { isImageFile(it.name) }?.toList() ?: emptyList()
    }

    private fun isImageFile(name: String): Boolean {
        val extensions = listOf(".jpg", ".jpeg", ".png", ".webp")
        return extensions.any { name.lowercase().endsWith(it) }
    }
}
