package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NewPipeYTMusicExtractor"

/**
 * Wrapper around NewPipe Extractor for YouTube Music streaming.
 * 
 * NewPipe Extractor is a proven library used by the NewPipe app (35k+ stars)
 * that extracts YouTube stream URLs without using the official API.
 * 
 * This solves the "UNPLAYABLE" issue we were facing with direct API calls.
 */
@Singleton
class NewPipeYTMusicExtractor @Inject constructor() {

    init {
        // Initialize NewPipe with a downloader
        NewPipe.init(NewPipeDownloader.getInstance())
    }

    /**
     * Get stream URL for a YouTube video ID.
     * 
     * @param videoId YouTube video ID (e.g. "dQw4w9WgXcQ")
     * @return Best quality audio stream URL, or null if unavailable
     */
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val youtube = ServiceList.YouTube
            val url = "https://music.youtube.com/watch?v=$videoId"
            
            Log.d(TAG, "Extracting stream for: $videoId")
            
            // Extract stream info
            val extractor = youtube.getStreamExtractor(url)
            extractor.fetchPage()
            
            // Get audio streams
            val audioStreams = extractor.audioStreams
            
            if (audioStreams.isEmpty()) {
                Log.e(TAG, "No audio streams found for: $videoId")
                return@withContext null
            }
            
            // Log all available streams for debugging
            Log.d(TAG, "Available audio streams for $videoId:")
            audioStreams.forEach { stream ->
                Log.d(TAG, "  - ${stream.format?.name} @ ${stream.averageBitrate} bps (${stream.format?.mimeType})")
            }
            
            // Get best quality audio stream (highest bitrate)
            // YouTube Music Premium offers up to 256kbps Opus
            val bestStream = audioStreams.maxByOrNull { it.averageBitrate }
            
            if (bestStream != null) {
                Log.d(TAG, "Selected BEST stream: ${bestStream.format?.name} @ ${bestStream.averageBitrate} bps")
                return@withContext bestStream.url
            } else {
                Log.e(TAG, "Could not select best stream for: $videoId")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract stream for $videoId: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Get detailed stream info including multiple quality options.
     * 
     * @param videoId YouTube video ID
     * @return List of available audio streams with quality info
     */
    suspend fun getAvailableStreams(videoId: String): List<AudioStreamInfo> = withContext(Dispatchers.IO) {
        try {
            val youtube = ServiceList.YouTube
            val url = "https://music.youtube.com/watch?v=$videoId"
            
            val extractor = youtube.getStreamExtractor(url)
            extractor.fetchPage()
            
            val audioStreams = extractor.audioStreams
            
            return@withContext audioStreams.map { stream ->
                AudioStreamInfo(
                    url = stream.url ?: "",
                    format = stream.format?.name ?: "unknown",
                    bitrate = stream.averageBitrate,
                    mimeType = stream.format?.mimeType ?: "audio/unknown"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available streams for $videoId: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Search YouTube Music for songs.
     * 
     * @param query Search query
     * @param maxResults Maximum number of results (default 20)
     * @return List of search results
     */
    suspend fun search(query: String, maxResults: Int = 20): List<YTMusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            val youtube = ServiceList.YouTube
            val searchExtractor = youtube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val results = mutableListOf<YTMusicSearchResult>()
            
            for (item in searchExtractor.initialPage.items) {
                if (item is StreamInfoItem && results.size < maxResults) {
                    results.add(
                        YTMusicSearchResult(
                            videoId = item.url.substringAfter("v=").substringBefore("&"),
                            title = item.name,
                            artist = item.uploaderName ?: "Unknown",
                            thumbnailUrl = item.thumbnails.maxByOrNull { it.width }?.url ?: "",
                            duration = item.duration
                        )
                    )
                }
            }
            
            Log.d(TAG, "Search for '$query' returned ${results.size} results")
            return@withContext results
            
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query': ${e.message}", e)
            return@withContext emptyList()
        }
    }
}

/**
 * Audio stream information.
 */
data class AudioStreamInfo(
    val url: String,
    val format: String,
    val bitrate: Int,
    val mimeType: String
)

/**
 * YouTube Music search result.
 */
data class YTMusicSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: Long
)
