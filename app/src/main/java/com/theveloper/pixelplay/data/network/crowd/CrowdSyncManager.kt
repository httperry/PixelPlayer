package com.theveloper.pixelplay.data.network.crowd

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrowdSyncManager @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    
    private val _roomState = MutableStateFlow<String?>("Disconnected")
    val roomState: StateFlow<String?> = _roomState.asStateFlow()

    fun joinRoom(roomId: String, userId: String) {
        val request = Request.Builder()
            .url("wss://crowdsync.theveloper.com/room/$roomId?user=$userId") // Placeholder URL
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("CrowdSync", "Joined Room $roomId")
                _roomState.value = "Connected to $roomId"
                val joinMessage = JSONObject().apply {
                    put("type", "JOIN_ROOM")
                    put("userId", userId)
                }
                webSocket.send(joinMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("CrowdSync", "Received: $text")
                try {
                    val json = JSONObject(text)
                    when(json.getString("type")) {
                        "SYNC_STATE" -> {
                            // Update local playback to match host
                        }
                        "PLAYBACK_COMMAND" -> {
                            // Execute play/pause
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CrowdSync", "Error parsing message", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _roomState.value = "Disconnected"
                Log.d("CrowdSync", "Closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                _roomState.value = "Error: ${t.message}"
                Log.e("CrowdSync", "Failure", t)
            }
        })
    }

    fun syncState(positionMs: Long, isPlaying: Boolean) {
        val msg = JSONObject().apply {
            put("type", "SYNC_STATE")
            put("positionMs", positionMs)
            put("isPlaying", isPlaying)
        }
        webSocket?.send(msg.toString())
    }

    fun sendPlaybackCommand(command: String) {
        val msg = JSONObject().apply {
            put("type", "PLAYBACK_COMMAND")
            put("command", command)
        }
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "User left")
        webSocket = null
        _roomState.value = "Disconnected"
    }
}
