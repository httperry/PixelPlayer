package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.YTMusicPythonService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.content.SharedPreferences
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

/**
 * Lightweight model for a search result page.
 *
 * @param hasMore  true while the second batch of results is still loading.
 */
data class YTMSearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<YTMAlbumShelf> = emptyList(),
    val hasMore: Boolean = false
)

/**
 * YouTube Music Repository
 *
 * Architecture:
 * - ALL operations go through Python ytmusicapi (WebSocket server, port 8765)
 * - Stream URL extraction uses Python yt-dlp (via "get_stream_url" WebSocket action)
 * - NewPipe Extractor has been fully removed
 *
 * Search uses a progressive loading strategy:
 * 1. First 10 results are returned immediately (fast)
 * 2. Next 10 results are loaded in the background and emitted as a second update
 *
 * CACHING: Room Database
 * - Offline access to songs and playlists
 * - Reduces WebSocket dependency at startup
 * - Cache-first strategy with background refresh
 */
@Singleton
class YTMusicRepository @Inject constructor(
    private val webSocketClient: YTMusicWebSocketClient,
    private val ytMusicDao: com.theveloper.pixelplay.data.database.YTMusicDao,
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("ytmusic_cache_prefs", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    private var connectionRetryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 2000L

    // Don't connect in init - wait for Python server to be ready
    // Connection will happen on first API call

    private fun connectWithRetry() {
        try {
            webSocketClient.connect()
            connectionRetryCount = 0
            Log.d(TAG, "WebSocket connected successfully")
        } catch (e: Exception) {
            connectionRetryCount++
            Log.e(TAG, "WebSocket connection failed (attempt $connectionRetryCount/$maxRetries): ${e.message}", e)

            if (connectionRetryCount < maxRetries) {
                // Retry after delay
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(retryDelayMs * connectionRetryCount)
                    connectWithRetry()
                }
            } else {
                Log.e(TAG, "WebSocket connection failed after $maxRetries attempts. Using cache-only mode.")
            }
        }
    }

    /**
     * Execute a WebSocket request with automatic retry on failure.
     * Falls back to cache if WebSocket is unavailable.
     * Ensures Python server is running and WebSocket is connected before executing.
     */
    private suspend fun <T> executeWithRetry(
        cacheProvider: (suspend () -> T?)? = null,
        operation: suspend () -> T
    ): T? {
        // Ensure WebSocket is connected (lazy connection)
        if (!webSocketClient.isConnected.value) {
            Log.d(TAG, "WebSocket not connected, attempting connection...")
            
            // Wait for Python server to be ready (max 10 seconds)
            var retries = 0
            while (!YTMusicPythonService.isRunning() && retries < 20) {
                kotlinx.coroutines.delay(500)
                retries++
            }
            
            if (!YTMusicPythonService.isRunning()) {
                Log.e(TAG, "Python server not running after 10 seconds, falling back to cache")
                return cacheProvider?.invoke()
            }
            
            // Python server is ready, connect WebSocket
            connectWithRetry()
            
            // Wait for connection (max 5 seconds)
            retries = 0
            while (!webSocketClient.isConnected.value && retries < 10) {
                kotlinx.coroutines.delay(500)
                retries++
            }
            
            if (!webSocketClient.isConnected.value) {
                Log.e(TAG, "WebSocket connection failed after 5 seconds, falling back to cache")
                return cacheProvider?.invoke()
            }
        }
        
        return try {
            operation()
        } catch (e: Exception) {
            Log.w(TAG, "WebSocket operation failed: ${e.message}. Attempting retry...")

            // Try reconnecting if not connected
            if (!webSocketClient.isConnected.value) {
                connectWithRetry()
                kotlinx.coroutines.delay(1000) // Wait for connection

                // Retry operation once
                try {
                    return operation()
                } catch (retryError: Exception) {
                    Log.e(TAG, "Retry failed: ${retryError.message}. Falling back to cache.")
                }
            }

            // Fall back to cache if available
            cacheProvider?.invoke()
        }
    }

    // =========================================================================
    // SEARCH — Progressive loading (10 results immediately, 10 more in background)
    // =========================================================================

    /**
     * Search YouTube Music for songs using progressive loading.
     *
     * Returns a [Flow] that emits two updates:
     * 1. First 10 results immediately (low latency — typically < 1s)
     * 2. All 20 results once the background batch completes
     *
     * Collectors should update the UI on each emission.
     *
     * Example:
     * ```kotlin
     * viewModelScope.launch {
     *     repo.searchSongsFlow(query).collect { results ->
     *         _uiState.value = results  // UI updates twice: fast first, complete second
     *     }
     * }
     * ```
     */
    fun searchSongsFlow(query: String): Flow<YTMSearchResults> = flow {
        YTMusicPythonService.keepAlive()

        // ── Batch 1: first 10 results (fast path) ────────────────────────────
        val firstResult = webSocketClient.searchPaginated(
            query = query,
            filter = "songs",
            limit = 10,
            offset = 0
        )

        val firstSongs = firstResult.getOrNull()?.mapNotNull { songData ->
            val parsed = YTMusicResponseParser.parseSearchResult(songData)
            parsed?.let { song -> cacheSearchSong(song) }
            parsed
        } ?: emptyList()

        // Emit first batch right away — UI becomes responsive immediately
        emit(YTMSearchResults(songs = firstSongs, hasMore = true))

        if (firstResult.isFailure) {
            Log.e(TAG, "Search batch 1 failed: ${firstResult.exceptionOrNull()?.message}")
            emit(YTMSearchResults(songs = firstSongs, hasMore = false))
            return@flow
        }

        // ── Batch 2: next 10 results (background) ────────────────────────────
        val secondResult = webSocketClient.searchPaginated(
            query = query,
            filter = "songs",
            limit = 10,
            offset = 10
        )

        val secondSongs = secondResult.getOrNull()?.mapNotNull { songData ->
            val parsed = YTMusicResponseParser.parseSearchResult(songData)
            parsed?.let { song -> cacheSearchSong(song) }
            parsed
        } ?: emptyList()

        if (secondResult.isFailure) {
            Log.w(TAG, "Search batch 2 failed: ${secondResult.exceptionOrNull()?.message}")
        }

        // Emit merged result (first + second batch)
        emit(YTMSearchResults(songs = firstSongs + secondSongs, hasMore = false))
    }

    /**
     * Get lightweight text search suggestions instantly.
     */
    suspend fun getSearchSuggestions(query: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (query.isBlank()) return@withContext emptyList()
                YTMusicPythonService.keepAlive()
                val result = webSocketClient.getSearchSuggestions(query)
                result.getOrNull() ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get search suggestions: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Convenience suspend function for callers that don't need progressive loading.
     * Waits for both batches and returns the complete list.
     */
    suspend fun searchSongs(query: String): YTMSearchResults {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                val result = webSocketClient.search(query, filter = "songs", limit = 20)
                result.onSuccess { results ->
                    val songs = results.mapNotNull { songData ->
                        val parsed = YTMusicResponseParser.parseSearchResult(songData)
                        parsed?.let { song -> cacheSearchSong(song) }
                        parsed
                    }
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

    private suspend fun cacheSearchSong(song: Song) {
        try {
            song.ytmusicId?.let { videoId ->
                ytMusicDao.insertSong(
                    com.theveloper.pixelplay.data.database.YTMusicSongEntity(
                        videoId = videoId,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.albumArtUriString ?: "",
                        durationSeconds = song.duration / 1000,
                        cachedAt = System.currentTimeMillis(),
                        lastAccessed = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache song ${song.ytmusicId}: ${e.message}")
        }
    }

    /**
     * Search YouTube Music for artists.
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
    // LIBRARY & PLAYLISTS
    // =========================================================================

    /**
     * Progressive library songs flow.
     *
     * Emits pages of songs as they arrive so the UI can display them
     * immediately without waiting for the full library to load.
     *
     * Strategy:
     * 1. Emit first 50 songs immediately (fast, ~1-2s)
     * 2. In parallel, fetch next 50 songs and emit them
     * 3. Continue until hasMore=false
     *
     * Each emission is cumulative (all songs seen so far).
     */
    fun getLibrarySongsFlow(pageSize: Int = 50): Flow<List<Song>> = flow {
        YTMusicPythonService.keepAlive()
        val allSongs = mutableListOf<Song>()
        var offset = 0
        var hasMore = true

        while (hasMore) {
            val result = webSocketClient.getLibrarySongs(offset = offset, limit = pageSize)
            val page = result.getOrNull() ?: break
            val parsed = page.songs.mapNotNull { YTMusicResponseParser.parseLibrarySong(it) }
            allSongs.addAll(parsed)
            emit(allSongs.toList())
            hasMore = page.hasMore
            offset += pageSize
        }
    }

    suspend fun getUserPlaylists(): List<com.theveloper.pixelplay.data.model.Playlist> {
        return withContext(Dispatchers.IO) {
            try {
                val freshPlaylists = executeWithRetry(
                    cacheProvider = {
                        Log.d(TAG, "Loading playlists from cache...")
                        val cached = ytMusicDao.getAllPlaylists()
                        cached.first().map { entity ->
                            com.theveloper.pixelplay.data.model.Playlist(
                                id = entity.playlistId,
                                name = entity.title,
                                songIds = emptyList(),
                                createdAt = entity.cachedAt,
                                source = "YTM",
                                coverImageUri = entity.thumbnailUrl
                            )
                        }
                    },
                    operation = {
                        YTMusicPythonService.keepAlive()

                        val result = webSocketClient.getLibraryPlaylists()
                        Log.d(TAG, "Raw library_playlists result: success=${result.isSuccess}, count=${result.getOrNull()?.size}")
                        result.getOrNull()?.mapNotNull { playlistData ->
                            Log.d(TAG, "Raw playlist keys: ${playlistData.keys}")
                            val parsed = YTMusicResponseParser.parsePlaylist(playlistData)
                            // ytmusicapi sometimes prepends "VL" to playlist IDs; strip it
                            val rawId = parsed["id"] as? String
                            val id = rawId?.removePrefix("VL")?.takeIf { it.isNotBlank() }
                            Log.d(TAG, "Parsed playlist: id=$id, title=${parsed["title"]}")
                            // Filter out playlists with null or empty IDs
                            if (id.isNullOrBlank()) {
                                Log.w(TAG, "Skipping playlist with blank id, raw keys: ${playlistData.keys}")
                                return@mapNotNull null
                            }
                            val title = parsed["title"] as? String ?: "Unknown"
                            val thumbnailUrl = parsed["thumbnailUrl"] as? String
                            val trackCount = (parsed["count"] as? Number)?.toInt() ?: 0

                            ytMusicDao.insertPlaylist(
                                com.theveloper.pixelplay.data.database.YTMusicPlaylistEntity(
                                    playlistId = id,
                                    title = title,
                                    thumbnailUrl = thumbnailUrl,
                                    trackCount = trackCount,
                                    cachedAt = System.currentTimeMillis(),
                                    lastSynced = System.currentTimeMillis()
                                )
                            )

                            com.theveloper.pixelplay.data.model.Playlist(
                                id = id,
                                name = title,
                                songIds = emptyList(),
                                createdAt = System.currentTimeMillis(),
                                source = "YTM",
                                coverImageUri = thumbnailUrl,
                                externalTrackCount = trackCount
                            )
                        } ?: emptyList()
                    }
                )

                freshPlaylists ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch YTM playlists: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Create a new playlist on YouTube Music.
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

    /**
     * Fetch the tracks of a YouTube Music playlist, returned as Song objects
     * ready for playback via the ytm:// URI scheme.
     *
     * Also updates the Room cache with the real track count so playlist cards
     * show the correct song count immediately on subsequent app launches.
     */
    suspend fun getPlaylistTracks(playlistId: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                Log.d(TAG, "Fetching tracks for YTM playlist: $playlistId")

                val result = webSocketClient.getPlaylist(playlistId, limit = 500)
                result.onSuccess { playlistData ->
                    @Suppress("UNCHECKED_CAST")
                    val tracks = playlistData["tracks"] as? List<Map<String, Any>> ?: emptyList()
                    Log.d(TAG, "YTM playlist $playlistId has ${tracks.size} tracks")
                    val songs = YTMusicResponseParser.parsePlaylistTracks(tracks)

                    // Persist the real track count back to Room so the playlist card
                    // shows the correct count immediately on the next app start.
                    if (songs.isNotEmpty()) {
                        try {
                            val existing = ytMusicDao.getPlaylist(playlistId)
                            if (existing != null && existing.trackCount != songs.size) {
                                ytMusicDao.insertPlaylist(
                                    existing.copy(
                                        trackCount = songs.size,
                                        lastSynced = System.currentTimeMillis()
                                    )
                                )
                                Log.d(TAG, "Updated trackCount for $playlistId: ${songs.size}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update trackCount for $playlistId: ${e.message}")
                        }
                    }

                    return@withContext songs
                }.onFailure { error ->
                    Log.e(TAG, "getPlaylist failed for $playlistId: ${error.message}", error)
                }

                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get playlist tracks for $playlistId: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Fetch YTM radio queue for a given videoId.
     * Returns the "up next" recommended songs as the player queue.
     */
    suspend fun getRadioQueue(videoId: String, limit: Int = 25): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()
                Log.d(TAG, "Fetching YTM radio queue for: $videoId")

                val result = webSocketClient.getWatchPlaylist(videoId = videoId, limit = limit)
                result.onSuccess { tracks ->
                    Log.d(TAG, "YTM radio queue: ${tracks.size} tracks for $videoId")
                    return@withContext YTMusicResponseParser.parsePlaylistTracks(tracks)
                }.onFailure { error ->
                    Log.e(TAG, "getWatchPlaylist failed for $videoId: ${error.message}", error)
                }

                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get radio queue for $videoId: ${e.message}", e)
                emptyList()
            }
        }
    }

    // =========================================================================
    // STREAMING — Python yt-dlp via WebSocket (replaces NewPipe Extractor)
    // =========================================================================


    /**
     * Get stream URL for playback.
     *
     * Delegates to Python yt-dlp backend via WebSocket.
     * yt-dlp is more reliable than NewPipe for stream extraction:
     * - Handles YouTube's cipher obfuscation automatically
     * - Actively maintained with frequent YouTube compatibility updates
     * - Runs in the existing Chaquopy Python process (no extra Java dependency)
     *
     * Stream URLs are cached server-side for 5 hours.
     */
    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()

                val result = webSocketClient.getStreamUrl(videoId)
                if (result.isSuccess) {
                    val streamUrl = result.getOrNull() ?: return@withContext null
                    Log.d(TAG, "yt-dlp extracted stream for: $videoId")
                    return@withContext YTMPlayerResponse(
                        videoDetails = VideoDetails(videoId = videoId),
                        streamingData = StreamingData(
                            adaptiveFormats = listOf(
                                AdaptiveFormat(
                                    url = streamUrl,
                                    mimeType = "audio/webm",
                                    bitrate = 128000
                                )
                            )
                        )
                    )
                }

                Log.w(TAG, "yt-dlp failed for video $videoId: ${result.exceptionOrNull()?.message}")
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
    // HOME FEED
    // =========================================================================

    /**
     * Get personalized home feed.
     */
    suspend fun getHomeDiscoverFeed(): List<YTMAlbumShelf> {
        return withContext(Dispatchers.IO) {
            // 1. Try to load from cache first and emit immediately? 
            // Wait, this function returns a List. To do "cache then network", 
            // we either need to return a Flow, or handle caching in the ViewModel.
            // Since it's a simple function, we can return the cached data if network fails,
            // or the ViewModel could call `getCachedHomeFeed()` then `getHomeDiscoverFeed()`.
            // Let's change the pattern to use Flow or just return the network result, but cache it.
            try {
                YTMusicPythonService.keepAlive()

                // Wait for Python backend to be authenticated (max 5 seconds)
                var authRetries = 0
                while (!YTMusicPythonService.isBackendAuthenticated() && authRetries < 10) {
                    kotlinx.coroutines.delay(500)
                    authRetries++
                }

                val result = webSocketClient.getHome()
                result.onSuccess { homeFeed ->
                    val parsed = YTMusicResponseParser.parseHomeFeed(homeFeed)
                    val shelves = parsed.map { section ->
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
                                contentUriString = "ytm://$videoId",
                                albumArtUriString = thumbnailUrl,
                                duration = 0L,
                                mimeType = "audio/webm",
                                bitrate = null,
                                sampleRate = null,
                                ytmusicId = videoId
                            )
                        }

                        YTMAlbumShelf(title, null, songs)
                    }
                    
                    // Save to cache
                    prefs.edit().putString("home_feed_cache", gson.toJson(shelves)).apply()
                    return@withContext shelves
                }.onFailure { error ->
                    Log.e(TAG, "Home feed failed: ${error.message}", error)
                }

                // Fallback to cache
                val cached = prefs.getString("home_feed_cache", null)
                if (cached != null) {
                    val type = object : TypeToken<List<YTMAlbumShelf>>() {}.type
                    return@withContext gson.fromJson(cached, type)
                }
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get YTM home feed: ${e.message}", e)
                val cached = prefs.getString("home_feed_cache", null)
                if (cached != null) {
                    val type = object : TypeToken<List<YTMAlbumShelf>>() {}.type
                    return@withContext gson.fromJson(cached, type)
                }
                emptyList()
            }
        }
    }
    
    suspend fun getCachedHomeDiscoverFeed(): List<YTMAlbumShelf>? {
        return withContext(Dispatchers.IO) {
            val cached = prefs.getString("home_feed_cache", null)
            if (cached != null) {
                val type = object : TypeToken<List<YTMAlbumShelf>>() {}.type
                gson.fromJson(cached, type)
            } else null
        }
    }
    
    suspend fun getRecentlyPlayed(): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                YTMusicPythonService.keepAlive()

                // Wait for Python backend to be authenticated (max 5 seconds)
                var authRetries = 0
                while (!YTMusicPythonService.isBackendAuthenticated() && authRetries < 10) {
                    kotlinx.coroutines.delay(500)
                    authRetries++
                }

                val result = webSocketClient.getHistory()
                result.onSuccess { history ->
                    val songs = history.mapNotNull { item ->
                        val videoId = item["videoId"] as? String ?: return@mapNotNull null
                        val songTitle = item["title"] as? String ?: "Unknown"
                        
                        @Suppress("UNCHECKED_CAST")
                        val artists = item["artists"] as? List<Map<String, Any>>
                        val artistName = artists?.firstOrNull()?.get("name") as? String ?: ""
                        
                        @Suppress("UNCHECKED_CAST")
                        val thumbnails = item["thumbnails"] as? List<Map<String, Any>>
                        val thumbnailUrl = YTMusicResponseParser.getBestThumbnail(thumbnails, 544)
                        
                        Song(
                            id = videoId,
                            title = songTitle,
                            artist = artistName,
                            artistId = -1L,
                            artists = emptyList(),
                            album = "",
                            albumId = -1L,
                            path = "",
                            contentUriString = "ytm://$videoId",
                            albumArtUriString = thumbnailUrl,
                            duration = 0L,
                            mimeType = "audio/webm",
                            bitrate = null,
                            sampleRate = null,
                            ytmusicId = videoId
                        )
                    }
                    
                    prefs.edit().putString("recently_played_cache", gson.toJson(songs)).apply()
                    return@withContext songs
                }.onFailure { error ->
                    Log.e(TAG, "History failed: ${error.message}", error)
                }

                // Fallback to cache
                val cached = prefs.getString("recently_played_cache", null)
                if (cached != null) {
                    val type = object : TypeToken<List<Song>>() {}.type
                    return@withContext gson.fromJson(cached, type)
                }
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get YTM history: ${e.message}", e)
                val cached = prefs.getString("recently_played_cache", null)
                if (cached != null) {
                    val type = object : TypeToken<List<Song>>() {}.type
                    return@withContext gson.fromJson(cached, type)
                }
                emptyList()
            }
        }
    }
    
    suspend fun getCachedRecentlyPlayed(): List<Song>? {
        return withContext(Dispatchers.IO) {
            val cached = prefs.getString("recently_played_cache", null)
            if (cached != null) {
                val type = object : TypeToken<List<Song>>() {}.type
                gson.fromJson(cached, type)
            } else null
        }
    }
    // =========================================================================
    // ARTIST PROFILE
    // =========================================================================

    /**
     * Get artist profile details.
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
