package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
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
import com.zionhuang.innertube.pages.SearchResult
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState

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


    /**
     * Execute a WebSocket request with automatic retry on failure.
     * Falls back to cache if WebSocket is unavailable.
     * Ensures Python server is running and WebSocket is connected before executing.
     */
    

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
        val result = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
        if (result != null) {
            val songs = result.items.mapNotNull { if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null }
            emit(YTMSearchResults(songs = songs, hasMore = false))
        } else {
            emit(YTMSearchResults(hasMore = false))
        }
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.searchSuggestions(query)
                result.getOrNull()?.queries ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Search suggestions failed: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun searchSongs(query: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG)
                val page = result.getOrNull() ?: return@withContext emptyList()
                page.items.mapNotNull { if (it is com.zionhuang.innertube.models.SongItem) it.toDomainSong() else null }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for '$query': ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun searchArtists(query: String): List<com.theveloper.pixelplay.data.model.Artist> {
        return withContext(Dispatchers.IO) {
            try {
                val result = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_ARTIST)
                val page = result.getOrNull() ?: return@withContext emptyList()
                page.items.mapNotNull { 
                    if (it is com.zionhuang.innertube.models.ArtistItem) {
                        com.theveloper.pixelplay.data.model.Artist(
                            id = it.id?.hashCode()?.toLong() ?: 0L,
                            name = it.title,
                            songCount = 0,
                            imageUrl = it.thumbnail
                        )
                    } else null 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search artists failed for '$query': ${e.message}", e)
                emptyList()
            }
        }
    }

        fun getLibrarySongsFlow(): Flow<PagingData<Song>> {
            return Pager(
                config = PagingConfig(pageSize = 50, enablePlaceholders = false),
                pagingSourceFactory = {
                    object : PagingSource<Int, Song>() {
                        override fun getRefreshKey(state: PagingState<Int, Song>): Int? = null
                        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> {
                            return try {
                                val result = com.zionhuang.innertube.YouTube.playlist("LM")
                                val playlist = result.getOrNull() ?: return LoadResult.Error(Exception("YTM Library error"))
                                val songs = playlist.songs.map { it.toDomainSong() }
                                LoadResult.Page(data = songs, prevKey = null, nextKey = null)
                            } catch (e: Exception) {
                                LoadResult.Error(e)
                            }
                        }
                    }
                }
            ).flow
        }

        suspend fun getUserPlaylists(): List<com.theveloper.pixelplay.data.model.Playlist> {
            return withContext(Dispatchers.IO) {
                try {
                    val result = com.zionhuang.innertube.YouTube.likedPlaylists()
                    val lists = result.getOrNull() ?: return@withContext emptyList()
                    lists.map { playlist ->
                        com.theveloper.pixelplay.data.model.Playlist(
                            id = playlist.id,
                            name = playlist.title,
                            songIds = emptyList(),
                            coverImageUri = playlist.thumbnail,
                            source = "YTM",
                            externalTrackCount = playlist.songCountText?.filter { it.isDigit() }?.toIntOrNull()
                        )
                    }
                } catch (e: Exception) {
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
                // InnerTube doesn't natively expose a simple create playlist helper in YouTube.kt
                // We stub this or call the raw API if needed. For now, just return null.
                Log.w(TAG, "createPlaylist natively not fully supported yet")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist: ${e.message}", e)
                null
            }
        }
    }

    suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "addVideoToPlaylist natively not fully supported yet")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add video to playlist: ${e.message}", e)
                false
            }
        }
    }

        suspend fun getPlaylist(playlistId: String): List<Song> {
            return withContext(Dispatchers.IO) {
                try {
                    val result = com.zionhuang.innertube.YouTube.playlist(playlistId)
                    val playlist = result.getOrNull() ?: return@withContext emptyList()
                    playlist.songs.map { it.toDomainSong() }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    /**
     * Fetch YTM radio queue for a given videoId.
     * Returns the "up next" recommended songs as the player queue.
     */
        suspend fun getPlaylistTracks(playlistId: String): List<Song> {
            return withContext(Dispatchers.IO) {
                try {
                    val result = com.zionhuang.innertube.YouTube.playlist(playlistId)
                    val page = result.getOrNull() ?: return@withContext emptyList()
                    page.songs.map { it.toDomainSong() }
                } catch (e: Exception) {
                    android.util.Log.e("YTMusicRepository", "Failed to fetch playlist tracks: $playlistId", e)
                    emptyList()
                }
            }
        }

        suspend fun getRadioQueue(videoId: String, limit: Int = 20): List<Song> {
            return withContext(Dispatchers.IO) {
                try {
                    val watchEndpoint = com.zionhuang.innertube.models.WatchEndpoint(videoId = videoId)
                    val result = com.zionhuang.innertube.YouTube.next(watchEndpoint, null)
                    val nextResult = result.getOrNull() ?: return@withContext emptyList()
                    return@withContext nextResult.items.map { it.toDomainSong() }
                } catch (e: Exception) {
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
                    val playerResponse = com.zionhuang.innertube.YouTube.player(videoId).getOrNull()
                    val formats = playerResponse?.streamingData?.adaptiveFormats ?: emptyList()
                    val audioFormat = formats
                        .filter { it.isAudio || it.mimeType.startsWith("audio/") }
                        .maxByOrNull { it.bitrate }
                    val streamUrl = audioFormat?.url ?: formats.firstOrNull { it.url != null }?.url
                    if (streamUrl != null) {
                        return@withContext YTMPlayerResponse(
                            videoDetails = VideoDetails(videoId = videoId),
                            streamingData = StreamingData(
                                adaptiveFormats = listOf(
                                    AdaptiveFormat(url = streamUrl, mimeType = audioFormat?.mimeType ?: "audio/webm", bitrate = audioFormat?.bitrate ?: 128000)
                                )
                            )
                        )
                    }
                    null
                } catch (e: Exception) {
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
        suspend fun getHome(): List<YTMAlbumShelf> { return emptyList() }
    
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
        return emptyList() // Stubbed for now to bypass websocket
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
                    val result = com.zionhuang.innertube.YouTube.artist(channelId)
                    val artistPage = result.getOrNull() ?: return@withContext null
                    
                    val songs = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
                    val albums = mutableListOf<YTMAlbumShelf>()
                    
                    artistPage.sections.forEach { section ->
                        section.items.forEach { item ->
                            when (item) {
                                is com.zionhuang.innertube.models.SongItem -> songs.add(item.toDomainSong())
                                is com.zionhuang.innertube.models.AlbumItem -> {
                                    albums.add(
                                        YTMAlbumShelf(
                                            title = item.title,
                                            browseId = item.browseId,
                                            songs = emptyList() // The artist page usually doesn't have the songs of the album directly, they have to be fetched
                                        )
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    YTMArtistProfile(
                        channelId = channelId,
                        name = artistPage.artist.title,
                        bio = artistPage.description,
                        monthlyListeners = "",
                        thumbnailUrl = artistPage.artist.thumbnail,
                        albums = albums,
                        topSongs = songs
                    )
                } catch (e: Exception) { null }
            }
        }
}


private fun com.zionhuang.innertube.models.SongItem.toDomainSong(): com.theveloper.pixelplay.data.model.Song {
    val artistName = this.artists.firstOrNull()?.name ?: "Unknown Artist"
    return com.theveloper.pixelplay.data.model.Song(
        id = "ytm_${this.id}",
        title = this.title,
        artist = artistName,
        artistId = 0L,
        album = this.album?.name ?: "Unknown Album",
        albumId = 0L,
        path = "",
        contentUriString = "ytm://${this.id}",
        albumArtUriString = this.thumbnail,
        duration = (this.duration?.toLong() ?: 0L) * 1000L,
        mimeType = "audio/mpeg",
        bitrate = 128000,
        sampleRate = 44100,
        ytmusicId = this.id
    )
}
