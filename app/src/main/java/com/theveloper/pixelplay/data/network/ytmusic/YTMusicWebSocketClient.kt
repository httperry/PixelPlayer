package com.theveloper.pixelplay.data.network.ytmusic

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.service.YTMusicPythonService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YTMusicWebSocketClient"
private const val WS_URL = "ws://127.0.0.1:8765"

/**
 * WebSocket client for Python YouTube Music API server.
 * 
 * Features:
 * - Fully async (never blocks UI thread)
 * - AES-256 encryption
 * - Automatic reconnection
 * - Request/response correlation
 * - StateFlow for reactive UI updates
 */
@Singleton
class YTMusicWebSocketClient @Inject constructor(
    private val sessionRepository: YTMSessionRepository
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // Keep-alive
        .build()

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var webSocket: WebSocket? = null
    private var encryptionKey: ByteArray? = null
    
    // Request/response correlation
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<Map<String, Any>>>()
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Reconnection
    private var reconnectJob: Job? = null
    private var shouldReconnect = true

    init {
        // Get encryption key from service
        encryptionKey = YTMusicPythonService.getEncryptionKey()?.let { key ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Base64.getDecoder().decode(key)
            } else {
                android.util.Base64.decode(key, android.util.Base64.NO_WRAP)
            }
        }
    }

    // ========================================================================
    // CONNECTION MANAGEMENT
    // ========================================================================

    fun connect() {
        if (webSocket != null) {
            Log.d(TAG, "Already connected")
            return
        }

        val request = Request.Builder()
            .url(WS_URL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket connected")
                reconnectJob?.cancel()
                
                // Set connected immediately so requests can be sent
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                _isConnected.value = false
                this@YTMusicWebSocketClient.webSocket = null
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                _isConnected.value = false
                this@YTMusicWebSocketClient.webSocket = null
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected.value = false
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000) // Wait 3 seconds
            if (shouldReconnect && !_isConnected.value) {
                Log.d(TAG, "Attempting reconnect...")
                connect()
            }
        }
    }

    // ========================================================================
    // ENCRYPTION (DISABLED - localhost only)
    // ========================================================================

    private fun encrypt(message: String): String {
        // No encryption for localhost communication
        return message
    }

    private fun decrypt(encrypted: String): String {
        // No decryption for localhost communication
        return encrypted
    }

    // ========================================================================
    // MESSAGE HANDLING
    // ========================================================================

    private suspend fun handleMessage(encryptedMessage: String) {
        try {
            val decrypted = decrypt(encryptedMessage)
            val response = gson.fromJson<Map<String, Any>>(
                decrypted,
                object : TypeToken<Map<String, Any>>() {}.type
            )
            
            val requestId = response["request_id"] as? String
            if (requestId != null) {
                pendingRequests[requestId]?.complete(response)
                pendingRequests.remove(requestId)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle message", e)
        }
    }

    private suspend fun sendRequest(
        action: String,
        data: Map<String, Any> = emptyMap()
    ): Result<Map<String, Any>> {
        // Keep service alive
        YTMusicPythonService.keepAlive()
        
        if (!_isConnected.value && action != "auth_setup") {
            Log.d(TAG, "Waiting for WebSocket connection...")
            try {
                kotlinx.coroutines.withTimeout(15000) {
                    isConnected.first { it }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                return Result.failure(Exception("Not connected (timeout waiting for Python server)"))
            }
        }

        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Map<String, Any>>()
        pendingRequests[requestId] = deferred

        val message = mapOf(
            "action" to action,
            "data" to data,
            "request_id" to requestId
        )

        val json = gson.toJson(message)
        val encrypted = encrypt(json)

        return try {
            webSocket?.send(encrypted)
            
            // Wait for response with timeout
            withTimeout(30000) {
                val response = deferred.await()
                
                if (response.containsKey("error")) {
                    Result.failure(Exception(response["error"] as String))
                } else {
                    Result.success(response)
                }
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            Result.failure(Exception("Request timeout"))
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Result.failure(e)
        }
    }

    // ========================================================================
    // API METHODS (All async, never block UI)
    // ========================================================================

    suspend fun setupAuth(cookies: String, sapisidHash: String): Result<Boolean> {
        return sendRequest("auth_setup", mapOf("cookies" to cookies, "sapisid_hash" to sapisidHash))
            .map { it["authenticated"] as? Boolean ?: false }
    }

    suspend fun search(
        query: String,
        filter: String = "songs",
        limit: Int = 10
    ): Result<List<Map<String, Any>>> {
        return sendRequest("search", mapOf(
            "query" to query,
            "filter" to filter,
            "limit" to limit
        )).map { response ->
            @Suppress("UNCHECKED_CAST")
            response["results"] as? List<Map<String, Any>> ?: emptyList()
        }
    }

    suspend fun getSearchSuggestions(query: String): Result<List<String>> {
        return sendRequest("search_suggestions", mapOf("query" to query)).map { response ->
            @Suppress("UNCHECKED_CAST")
            response["suggestions"] as? List<String> ?: emptyList()
        }
    }

    /**
     * Search with offset/limit for progressive loading.
     *
     * Call with offset=0 for the first page (fast), then offset=10 for the
     * second page in the background.
     */
    suspend fun searchPaginated(
        query: String,
        filter: String = "songs",
        limit: Int = 10,
        offset: Int = 0
    ): Result<List<Map<String, Any>>> {
        return sendRequest("search", mapOf(
            "query" to query,
            "filter" to filter,
            "limit" to limit,
            "offset" to offset
        )).map { response ->
            @Suppress("UNCHECKED_CAST")
            response["results"] as? List<Map<String, Any>> ?: emptyList()
        }
    }

    /** Get paginated library songs. offset=0, limit=50 returns the first 50 songs fast. */
    data class LibrarySongsPage(
        val songs: List<Map<String, Any>>,
        val offset: Int,
        val limit: Int,
        val total: Int,
        val hasMore: Boolean,
        val cached: Boolean
    )

    suspend fun getLibrarySongs(offset: Int = 0, limit: Int = 50): Result<LibrarySongsPage> {
        return sendRequest("library_songs", mapOf("offset" to offset, "limit" to limit)).map { response ->
            @Suppress("UNCHECKED_CAST")
            LibrarySongsPage(
                songs = response["songs"] as? List<Map<String, Any>> ?: emptyList(),
                offset = (response["offset"] as? Number)?.toInt() ?: offset,
                limit = (response["limit"] as? Number)?.toInt() ?: limit,
                total = (response["total"] as? Number)?.toInt() ?: 0,
                hasMore = response["has_more"] as? Boolean ?: false,
                cached = response["cached"] as? Boolean ?: false
            )
        }
    }

    suspend fun getLibraryPlaylists(): Result<List<Map<String, Any>>> {
        return sendRequest("library_playlists").map { response ->
            @Suppress("UNCHECKED_CAST")
            response["playlists"] as? List<Map<String, Any>> ?: emptyList()
        }
    }

    suspend fun getPlaylist(playlistId: String, limit: Int = 200): Result<Map<String, Any>> {
        return sendRequest("get_playlist", mapOf(
            "playlist_id" to playlistId,
            "limit" to limit
        )).map { response ->
            // Python now returns both 'playlist' (full object) and 'tracks' (flat list)
            // Merge them so callers can access 'tracks' directly from the result map
            @Suppress("UNCHECKED_CAST")
            val playlist = response["playlist"] as? Map<String, Any> ?: emptyMap()
            val tracks = response["tracks"]
                ?: playlist["tracks"]  // fallback: tracks nested inside playlist object
            if (tracks != null) {
                playlist + mapOf("tracks" to tracks)
            } else {
                playlist
            }
        }
    }

    suspend fun getWatchPlaylist(
        videoId: String,
        playlistId: String = "",
        limit: Int = 25
    ): Result<List<Map<String, Any>>> {
        return sendRequest("watch_playlist", mapOf(
            "video_id" to videoId,
            "playlist_id" to playlistId,
            "limit" to limit
        )).map { response ->
            @Suppress("UNCHECKED_CAST")
            response["tracks"] as? List<Map<String, Any>> ?: emptyList()
        }
    }

    suspend fun createPlaylist(
        title: String,
        description: String = "",
        privacy: String = "PRIVATE",
        videoIds: List<String> = emptyList()
    ): Result<String> {
        return sendRequest("create_playlist", mapOf(
            "title" to title,
            "description" to description,
            "privacy" to privacy,
            "video_ids" to videoIds
        )).map { it["playlist_id"] as? String ?: "" }
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Boolean> {
        return sendRequest("add_to_playlist", mapOf(
            "playlist_id" to playlistId,
            "video_ids" to videoIds
        )).map { it["success"] as? Boolean ?: false }
    }

    suspend fun removeFromPlaylist(playlistId: String, videoIds: List<String>): Result<Boolean> {
        return sendRequest("remove_from_playlist", mapOf(
            "playlist_id" to playlistId,
            "video_ids" to videoIds
        )).map { it["success"] as? Boolean ?: false }
    }

    suspend fun likeSong(videoId: String): Result<Boolean> {
        return sendRequest("like_song", mapOf("video_id" to videoId))
            .map { it["success"] as? Boolean ?: false }
    }

    suspend fun unlikeSong(videoId: String): Result<Boolean> {
        return sendRequest("unlike_song", mapOf("video_id" to videoId))
            .map { it["success"] as? Boolean ?: false }
    }

    suspend fun getHome(): Result<List<Map<String, Any>>> {
        return sendRequest("get_home").map { response ->
            @Suppress("UNCHECKED_CAST")
            response["home"] as? List<Map<String, Any>> ?: emptyList()
        }
    }

    suspend fun getHistory(): Result<List<Map<String, Any>>> {
        return sendRequest("get_history").map { response ->
            @Suppress("UNCHECKED_CAST")
            response["history"] as? List<Map<String, Any>> ?: emptyList()
        }
    }

    suspend fun getArtist(browseId: String): Result<Map<String, Any>> {
        return sendRequest("get_artist", mapOf("browse_id" to browseId))
            .map { response ->
                @Suppress("UNCHECKED_CAST")
                response["artist"] as? Map<String, Any> ?: emptyMap()
            }
    }

    /**
     * Get audio stream URL for a YouTube video ID.
     *
     * Delegates to Python yt-dlp backend for reliable stream extraction.
     * yt-dlp handles YouTube's cipher obfuscation and is actively maintained.
     *
     * @param videoId 11-character YouTube video ID
     * @return Result containing the direct audio stream URL string
     */
    suspend fun getStreamUrl(videoId: String): Result<String> {
        return sendRequest("get_stream_url", mapOf("video_id" to videoId))
            .map { response ->
                response["stream_url"] as? String
                    ?: throw Exception("No stream_url in response")
            }
    }
}
