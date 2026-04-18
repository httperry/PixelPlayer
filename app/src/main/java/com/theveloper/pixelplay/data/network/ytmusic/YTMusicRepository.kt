package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.YTMusicPythonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YTMusicRepository"

/** Lightweight model for a YTM artist page, used to hydrate ArtistDetailUiState. */
data class YTMArtistProfile(
    val channelId: String,
    val name: String,
    val bio: String?,
    val monthlyListeners: String?,
    val thumbnailUrl: String?,
    val albums: List<YTMAlbumShelf> = emptyList(),
    val topSongs: List<Song> = emptyList()
)

data class YTMAlbumShelf(
    val title: String,
    val browseId: String?,
    val songs: List<Song> = emptyList()
)

/** Lightweight model for a search result page. */
data class YTMSearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<YTMAlbumShelf> = emptyList()
)

/**
 * YouTube Music Repository - Hybrid Architecture
 * 
 * PRIMARY: Python ytmusicapi (via WebSocket)
 * - All metadata, search, library, playlists
 * - Account authentication & sync
 * - High-quality images (544x544+)
 * - Two-way sync (read & write)
 * 
 * SECONDARY: NewPipe Extractor
 * - Stream URL extraction only
 * - Reliable playback
 * 
 * NO HTTP API FALLBACK - WebSocket only for speed
 */
@Singleton
class YTMusicRepository @Inject constructor(
    private val webSocketClient: YTMusicWebSocketClient,
    private val newPipeExtractor: NewPipeYTMusicExtractor
) {

    init {
        // Connect WebSocket client on initialization
        webSocketClient.connect()
    }

    // =========================================================================
    // SEARCH - Python ytmusicapi via WebSocket
    // =========================================================================

    /**
     * Search YouTube Music for songs.
     * Uses Python ytmusicapi with your account context.
     */
    suspend fun searchSongs(query: String): YTMSearchResults {
        return withContext(Dispatchers.IO) {
            try {
                // Keep service alive
                YTMusicPythonService.keepAlive()
                
                val result = webSocketClient.search(query, filter = "songs", limit = 20)
                result.onSuccess { results ->
                    val songs = results.mapNotNull { YTMusicResponseParser.parseSearchResult(it) }
                    return@withContext YTMSearchResults(songs = songs)
                }.onFailure { error ->
                    Log.e(TAG, "Search failed: ${error.message}", error)
                }
                
                YTMSearchResults()
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for '$query': ${e.message}", e)
                YTMSearchResults()
            }
        }
    }

    /**
     * Search YouTube Music for artists.
     * Uses Python ytmusicapi with your account context.
     */
    suspend fun searchArtists(query: String): List<com.theveloper.pixelplay.data.model.Artist> {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                
                val result = webSocketClient.search(query, filter = "artists", limit = 20)
                result.onSuccess { results ->
                    return@withContext results.mapNotNull { artistData ->
                        val name = artistData["name"] as? String ?: return@mapNotNull null
                        val browseId = artistData["browseId"] as? String
                        
                        @Suppress("UNCHECKED_CAST")
                        val thumbnails = artistData["thumbnails"] as? List<Map<String, Any>>
                        val imageUrl = YTMusicResponseParser.getBestThumbnail(thumbnails, 544)
                        
                        com.theveloper.pixelplay.data.model.Artist(
                            id = browseId?.hashCode()?.toLong() ?: 0L,
                            name = name,
                            songCount = 0,
                            imageUrl = imageUrl
                        )
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Artist search failed: ${error.message}", error)
                }
                
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Search artists failed for '$query': ${e.message}", e)
                emptyList()
            }
        }
    }

    // =========================================================================
    // LIBRARY & PLAYLISTS - Python ytmusicapi via WebSocket
    // =========================================================================

    /**
     * Get user's YouTube Music playlists.
     * Requires authentication via cookies.
     */
    suspend fun getUserPlaylists(): List<com.theveloper.pixelplay.data.model.Playlist> {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                
                val result = webSocketClient.getLibraryPlaylists()
                result.onSuccess { playlists ->
                    return@withContext playlists.mapNotNull { playlistData ->
                        val parsed = YTMusicResponseParser.parsePlaylist(playlistData)
                        val id = parsed["id"] as? String ?: return@mapNotNull null
                        val title = parsed["title"] as? String ?: "Unknown"
                        val thumbnailUrl = parsed["thumbnailUrl"] as? String
                        
                        com.theveloper.pixelplay.data.model.Playlist(
                            id = id,
                            name = title,
                            songIds = emptyList(),
                            createdAt = System.currentTimeMillis(),
                            source = "YTM",
                            coverImageUri = thumbnailUrl
                        )
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Get playlists failed: ${error.message}", error)
                }
                
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch YTM playlists: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Create a new playlist on YouTube Music.
     * Requires authentication.
     */
    suspend fun createPlaylist(
        title: String, 
        description: String = "", 
        videoIds: List<String> = emptyList()
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                
                val result = webSocketClient.createPlaylist(title, description, "PRIVATE", videoIds)
                result.onSuccess { playlistId ->
                    return@withContext playlistId
                }.onFailure { error ->
                    Log.e(TAG, "Create playlist failed: ${error.message}", error)
                }
                
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Add video to playlist on YouTube Music.
     * Requires authentication.
     */
    suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                
                val result = webSocketClient.addToPlaylist(playlistId, listOf(videoId))
                result.onSuccess { success ->
                    return@withContext success
                }.onFailure { error ->
                    Log.e(TAG, "Add to playlist failed: ${error.message}", error)
                }
                
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add video to playlist: ${e.message}", e)
                false
            }
        }
    }

    // =========================================================================
    // STREAMING - NewPipe Extractor (reliable stream URLs)
    // =========================================================================

    /**
     * Get stream URL for playback.
     * 
     * Uses NewPipe Extractor for reliable streaming.
     * Works for public content without authentication.
     */
    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
        return withContext(Dispatchers.IO) {
            try {
                // Use NewPipe Extractor for stream URL
                val streamUrl = newPipeExtractor.getStreamUrl(videoId)
                
                if (streamUrl != null) {
                    Log.d(TAG, "NewPipe extracted stream for: $videoId")
                    return@withContext YTMPlayerResponse(
                        videoDetails = VideoDetails(videoId = videoId),
                        streamingData = StreamingData(
                            adaptiveFormats = listOf(
                                AdaptiveFormat(
                                    url = streamUrl,
                                    mimeType = "audio/mp4",
                                    bitrate = 128000
                                )
                            )
                        )
                    )
                }
                
                Log.w(TAG, "NewPipe failed for video $videoId")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get stream for video $videoId: ${e.message}", e)
                null
            }
        }
    }

    suspend fun getPlayerAudioConfig(videoId: String): AudioConfig? {
        return getPlayerRawStream(videoId)?.playerConfig?.audioConfig
    }

    // =========================================================================
    // HOME FEED - Python ytmusicapi via WebSocket
    // =========================================================================

    /**
     * Get personalized home feed.
     * Uses Python ytmusicapi with your account context.
     */
    suspend fun getHomeDiscoverFeed(): List<YTMAlbumShelf> {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                
                val result = webSocketClient.getHome()
                result.onSuccess { homeFeed ->
                    val parsed = YTMusicResponseParser.parseHomeFeed(homeFeed)
                    return@withContext parsed.map { section ->
                        val title = section["title"] as? String ?: "Recommendations"
                        @Suppress("UNCHECKED_CAST")
                        val contents = section["contents"] as? List<Map<String, Any>> ?: emptyList()
                        
                        val songs = contents.mapNotNull { item ->
                            val videoId = item["videoId"] as? String ?: return@mapNotNull null
                            val songTitle = item["title"] as? String ?: "Unknown"
                            val thumbnailUrl = item["thumbnailUrl"] as? String
                            
                            Song(
                                id = videoId,
                                title = songTitle,
                                artist = "",
                                artistId = -1L,
                                artists = emptyList(),
                                album = "",
                                albumId = -1L,
                                path = "",
                                contentUriString = "ytmusic://$videoId",
                                albumArtUriString = thumbnailUrl,
                                duration = 0L,
                                mimeType = "audio/mp4",
                                bitrate = null,
                                sampleRate = null,
                                ytmusicId = videoId
                            )
                        }
                        
                        YTMAlbumShelf(title, null, songs)
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Home feed failed: ${error.message}", error)
                }
                
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get YTM home feed: ${e.message}", e)
                emptyList()
            }
        }
    }

    // =========================================================================
    // ARTIST PROFILE - Python ytmusicapi via WebSocket
    // =========================================================================

    /**
     * Get artist profile details.
     * Uses Python ytmusicapi with your account context.
     */
    suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                
                val result = webSocketClient.getArtist(channelId)
                result.onSuccess { artistData ->
                    val parsed = YTMusicResponseParser.parseArtist(artistData)
                    return@withContext YTMArtistProfile(
                        channelId = channelId,
                        name = parsed["name"] as? String ?: "Unknown",
                        bio = parsed["description"] as? String,
                        monthlyListeners = parsed["subscribers"] as? String,
                        thumbnailUrl = parsed["thumbnailUrl"] as? String,
                        albums = emptyList(),
                        topSongs = emptyList()
                    )
                }.onFailure { error ->
                    Log.e(TAG, "Get artist failed: ${error.message}", error)
                }
                
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch artist profile for $channelId: ${e.message}", e)
                null
            }
        }
    }
}
