package com.theveloper.pixelplay.data.network.ytmusic

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YTDLPMusicExtractor"

/**
 * Comprehensive YouTube Music extractor using yt-dlp.
 * 
 * This provides EVERYTHING:
 * - Search with full metadata
 * - Playlist contents
 * - High-quality thumbnails
 * - Stream URLs
 * - Artist info
 * - Album info
 * 
 * More reliable than manual API calls because yt-dlp is actively
 * maintained and handles YouTube's constant changes.
 */
@Singleton
class YTDLPMusicExtractor @Inject constructor(
    private val context: Context
) {

    init {
        try {
            // Initialize yt-dlp
            YoutubeDL.getInstance().init(context)
            Log.d(TAG, "yt-dlp initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize yt-dlp: ${e.message}", e)
        }
    }

    /**
     * Search YouTube Music with full metadata.
     * Returns high-quality results with proper thumbnails.
     */
    suspend fun search(query: String, maxResults: Int = 20): List<YTMusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest("ytsearch$maxResults:$query")
            request.addOption("--dump-json")
            request.addOption("--flat-playlist")
            request.addOption("--default-search", "ytsearch")
            
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out
            
            // Parse JSON results
            val results = mutableListOf<YTMusicSearchResult>()
            output.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val json = JSONObject(line)
                        results.add(
                            YTMusicSearchResult(
                                videoId = json.optString("id", ""),
                                title = json.optString("title", "Unknown"),
                                artist = json.optString("uploader", "Unknown"),
                                thumbnailUrl = getBestThumbnail(json),
                                duration = json.optLong("duration", 0)
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse search result: ${e.message}")
                    }
                }
            }
            
            Log.d(TAG, "Search for '$query' returned ${results.size} results")
            return@withContext results
            
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query': ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Get playlist contents with all songs.
     * Solves the "empty playlist" problem!
     */
    suspend fun getPlaylistSongs(playlistId: String): List<YTMusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.youtube.com/playlist?list=$playlistId"
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            request.addOption("--flat-playlist")
            
            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out
            
            val songs = mutableListOf<YTMusicSearchResult>()
            output.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val json = JSONObject(line)
                        songs.add(
                            YTMusicSearchResult(
                                videoId = json.optString("id", ""),
                                title = json.optString("title", "Unknown"),
                                artist = json.optString("uploader", "Unknown"),
                                thumbnailUrl = getBestThumbnail(json),
                                duration = json.optLong("duration", 0)
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse playlist item: ${e.message}")
                    }
                }
            }
            
            Log.d(TAG, "Playlist $playlistId has ${songs.size} songs")
            return@withContext songs
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get playlist $playlistId: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Get stream URL for playback.
     * Can be used as fallback if NewPipe fails.
     */
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.youtube.com/watch?v=$videoId"
            val request = YoutubeDLRequest(url)
            request.addOption("--get-url")
            request.addOption("--format", "bestaudio")
            
            val response = YoutubeDL.getInstance().execute(request)
            val streamUrl = response.out.trim()
            
            if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
                Log.d(TAG, "Got stream URL for $videoId")
                return@withContext streamUrl
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stream URL for $videoId: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Get detailed video info including all metadata.
     */
    suspend fun getVideoInfo(videoId: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.youtube.com/watch?v=$videoId"
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            
            val response = YoutubeDL.getInstance().execute(request)
            val json = JSONObject(response.out)
            
            return@withContext VideoInfo(
                videoId = json.optString("id", videoId),
                title = json.optString("title", "Unknown"),
                artist = json.optString("uploader", "Unknown"),
                album = json.optString("album", null),
                thumbnailUrl = getBestThumbnail(json),
                duration = json.optLong("duration", 0),
                description = json.optString("description", null)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video info for $videoId: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Extract best quality thumbnail from JSON response.
     * Solves the "blurry thumbnail" problem!
     */
    private fun getBestThumbnail(json: JSONObject): String {
        try {
            val thumbnails = json.optJSONArray("thumbnails")
            if (thumbnails != null && thumbnails.length() > 0) {
                // Get highest resolution thumbnail
                var bestUrl = ""
                var maxWidth = 0
                
                for (i in 0 until thumbnails.length()) {
                    val thumb = thumbnails.getJSONObject(i)
                    val width = thumb.optInt("width", 0)
                    val url = thumb.optString("url", "")
                    
                    if (width > maxWidth && url.isNotBlank()) {
                        maxWidth = width
                        bestUrl = url
                    }
                }
                
                if (bestUrl.isNotBlank()) {
                    return bestUrl
                }
            }
            
            // Fallback to thumbnail field
            return json.optString("thumbnail", "")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract thumbnail: ${e.message}")
            return json.optString("thumbnail", "")
        }
    }
}

/**
 * Detailed video information.
 */
data class VideoInfo(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnailUrl: String,
    val duration: Long,
    val description: String?
)
