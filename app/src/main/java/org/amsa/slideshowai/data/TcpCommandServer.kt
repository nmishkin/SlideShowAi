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

    suspend fun start(port: Int, handler: suspend (String, List<String>) -> String) {
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

    private suspend fun handleClient(socket: Socket, handler: suspend (String, List<String>) -> String) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                val inputLine = reader.readLine()
                if (inputLine != null) {
                    Log.d("TcpCommandServer", "Received: $inputLine")
                    try {
                        val json = JSONObject(inputLine)
                        val cmd = json.optString("cmd")
                        val argsJson = json.optJSONArray("args")
                        val args = mutableListOf<String>()
                        if (argsJson != null) {
                            for (i in 0 until argsJson.length()) {
                                args.add(argsJson.getString(i))
                            }
                        }

                        val response = handler(cmd, args)
                        writer.println(response)
                    } catch (e: Exception) {
                        Log.e("TcpCommandServer", "Error processing command", e)
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

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("TcpCommandServer", "Error closing server", e)
        }
    }
}
