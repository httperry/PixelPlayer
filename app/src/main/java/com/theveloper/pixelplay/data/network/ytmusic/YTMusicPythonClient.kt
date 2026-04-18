package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YTMusicPythonClient"
private const val BASE_URL = "http://127.0.0.1:8765"

/**
 * Fast Kotlin client for Python YouTube Music API server.
 * 
 * This client makes HTTP calls to the local Python server running in the background.
 * Responses are instant because the Python server is always initialized and ready.
 * 
 * Performance:
 * - First call: ~50-100ms (Python already running)
 * - Subsequent calls: ~10-30ms (cached data)
 * - No startup delay!
 */
@Singleton
class YTMusicPythonClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ========================================================================
    // AUTHENTICATION
    // ========================================================================

    suspend fun setupAuth(cookies: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("cookies" to cookies))
            val response = post("/auth/setup", json)
            
            val result = gson.fromJson(response, Map::class.java)
            Result.success(result["authenticated"] as? Boolean ?: false)
        } catch (e: Exception) {
            Log.e(TAG, "Auth setup failed", e)
            Result.failure(e)
        }
    }

    suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = get("/auth/status")
            val result = gson.fromJson(response, Map::class.java)
            result["authenticated"] as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Auth status check failed", e)
            false
        }
    }

    // ========================================================================
    // SEARCH
    // ========================================================================

    suspend fun search(
        query: String,
        filter: String = "songs",
        limit: Int = 20
    ): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf(
                "query" to query,
                "filter" to filter,
                "limit" to limit
            ))
            
            val response = post("/search", json)
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            val results = result["results"] as? List<Map<String, Any>> ?: emptyList()
            
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // LIBRARY
    // ========================================================================

    suspend fun getLibrarySongs(): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/library/songs")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            val songs = result["songs"] as? List<Map<String, Any>> ?: emptyList()
            
            Result.success(songs)
        } catch (e: Exception) {
            Log.e(TAG, "Get library songs failed", e)
            Result.failure(e)
        }
    }

    suspend fun getLibraryPlaylists(): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/library/playlists")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            val playlists = result["playlists"] as? List<Map<String, Any>> ?: emptyList()
            
            Result.success(playlists)
        } catch (e: Exception) {
            Log.e(TAG, "Get library playlists failed", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // PLAYLISTS
    // ========================================================================

    suspend fun getPlaylist(playlistId: String, limit: Int = 100): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/playlist/$playlistId?limit=$limit")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            val playlist = result["playlist"] as? Map<String, Any> ?: emptyMap()
            
            Result.success(playlist)
        } catch (e: Exception) {
            Log.e(TAG, "Get playlist failed", e)
            Result.failure(e)
        }
    }

    suspend fun createPlaylist(
        title: String,
        description: String = "",
        privacy: String = "PRIVATE",
        videoIds: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf(
                "title" to title,
                "description" to description,
                "privacy" to privacy,
                "video_ids" to videoIds
            ))
            
            val response = post("/playlist/create", json)
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            val playlistId = result["playlist_id"] as? String ?: ""
            
            Result.success(playlistId)
        } catch (e: Exception) {
            Log.e(TAG, "Create playlist failed", e)
            Result.failure(e)
        }
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("video_ids" to videoIds))
            val response = post("/playlist/$playlistId/add", json)
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            
            Result.success(result["success"] as? Boolean ?: false)
        } catch (e: Exception) {
            Log.e(TAG, "Add to playlist failed", e)
            Result.failure(e)
        }
    }

    suspend fun removeFromPlaylist(playlistId: String, videoIds: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(mapOf("video_ids" to videoIds))
            val response = post("/playlist/$playlistId/remove", json)
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            
            Result.success(result["success"] as? Boolean ?: false)
        } catch (e: Exception) {
            Log.e(TAG, "Remove from playlist failed", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // LIKES
    // ========================================================================

    suspend fun likeSong(videoId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = post("/like/$videoId", "{}")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            
            Result.success(result["success"] as? Boolean ?: false)
        } catch (e: Exception) {
            Log.e(TAG, "Like song failed", e)
            Result.failure(e)
        }
    }

    suspend fun unlikeSong(videoId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = post("/unlike/$videoId", "{}")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            
            Result.success(result["success"] as? Boolean ?: false)
        } catch (e: Exception) {
            Log.e(TAG, "Unlike song failed", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // RECOMMENDATIONS
    // ========================================================================

    suspend fun getHome(): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/home")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            val home = result["home"] as? List<Map<String, Any>> ?: emptyList()
            
            Result.success(home)
        } catch (e: Exception) {
            Log.e(TAG, "Get home failed", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // ARTIST
    // ========================================================================

    suspend fun getArtist(browseId: String): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val response = get("/artist/$browseId")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            val artist = result["artist"] as? Map<String, Any> ?: emptyMap()
            
            Result.success(artist)
        } catch (e: Exception) {
            Log.e(TAG, "Get artist failed", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // HEALTH CHECK
    // ========================================================================

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = get("/health")
            val result = gson.fromJson<Map<String, Any>>(response, object : TypeToken<Map<String, Any>>() {}.type)
            result["status"] == "ok"
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            false
        }
    }

    // ========================================================================
    // HTTP HELPERS
    // ========================================================================

    private fun get(path: String): String {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }

    private fun post(path: String, json: String): String {
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }
}
