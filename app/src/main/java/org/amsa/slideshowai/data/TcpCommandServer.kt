package org.amsa.slideshowai.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TcpCommandServer {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    suspend fun start(port: Int, handler: suspend (String, JSONObject, Socket) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.d("TcpCommandServer", "Server started on port $port")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            handleClient(socket, handler)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e("TcpCommandServer", "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TcpCommandServer", "Error starting server", e)
            }
        }
    }

    private suspend fun handleClient(socket: Socket, handler: suspend (String, JSONObject, Socket) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                // We don't use a buffered reader because we might need to read raw bytes
                val inputStream = socket.getInputStream()
                
                // Read line by line (for JSON commands)
                // We implement a simple robust line reader that doesn't over-buffer
                while (true) {
                    val line = readLineFromStream(inputStream)
                    if (line.isNullOrBlank()) break
                    
                    Log.d("TcpCommandServer", "Received: $line")
                    try {
                        val json = JSONObject(line)
                        val cmd = json.optString("cmd")
                        
                        // Pass the socket/stream to the handler so it can read binary data if needed
                        handler(cmd, json, socket)
                        
                        // Handler is responsible for writing response
                    } catch (e: Exception) {
                        Log.e("TcpCommandServer", "Error processing command", e)
                         val writer = PrintWriter(socket.getOutputStream(), true)
                        val errorResponse = JSONObject().apply {
                            put("status", "error")
                            put("message", "Invalid JSON or command format: ${e.message}")
                        }
                        writer.println(errorResponse.toString())
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("TcpCommandServer", "Error handling client", e)
            }
        }
    }
    
    // Reads a line ending with \n, byte by byte to avoid buffering future binary data
    private fun readLineFromStream(inputStream: java.io.InputStream): String? {
        val stringBuilder = StringBuilder()
        var char = inputStream.read()
        while (char != -1) {
            if (char == '\n'.code) {
                return stringBuilder.toString()
            }
            stringBuilder.append(char.toChar())
            char = inputStream.read()
        }
        return if (stringBuilder.isNotEmpty()) stringBuilder.toString() else null
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("TcpCommandServer", "Error closing server", e)
        }
    }
}
