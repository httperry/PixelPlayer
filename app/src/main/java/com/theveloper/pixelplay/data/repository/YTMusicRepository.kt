package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
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
 * High-level repository that calls [YTMusicApi] and maps raw JSON responses
 * into PixelPlayer's existing [Song] and artist models.
 *
 * HYBRID APPROACH:
 * - Python ytmusicapi (via WebSocket) for metadata, search, library, playlists
 * - NewPipe Extractor for reliable stream URLs
 * - Legacy HTTP API as fallback
 *
 * All parsing is done here so ViewModels never touch raw API types.
 */
@Singleton
class YTMusicRepository @Inject constructor(
    private val api: YTMusicApi,
    private val newPipeExtractor: NewPipeYTMusicExtractor,
    private val webSocketClient: YTMusicWebSocketClient
) {
    
    init {
        // Connect WebSocket client on initialization
        webSocketClient.connect()
    }

    // -------------------------------------------------------------------------
    // Artist profile
    // -------------------------------------------------------------------------

    /**
     * Fetches a full artist profile from /browse using the YouTube channel ID.
     *
     * @param channelId The YTM artist browse ID (format: "UCxxxxxxxx…")
     */
    suspend fun getArtistProfile(channelId: String): YTMArtistProfile? {
        return try {
            val response = api.browse(
                request = YTMBrowseRequest(browseId = channelId)
            )

            val header = response.header?.musicImmersiveHeaderRenderer
            val name = header?.title?.text() ?: "Unknown Artist"
            val bio = header?.description?.text()?.takeIf { it.isNotBlank() }
            val monthlyListeners = header
                ?.subscriptionButton
                ?.subscribeButtonRenderer
                ?.subscriberCountText
                ?.text()

            val thumbnailUrl = header?.thumbnail?.thumbnails?.bestUrl(1080)

            // Parse top songs / albums from the section list
            val sections = response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs?.firstOrNull()
                ?.tabRenderer?.content
                ?.sectionListRenderer?.contents

            val topSongs = mutableListOf<Song>()
            val albums = mutableListOf<YTMAlbumShelf>()

            sections?.forEach { section ->
                val shelf = section.musicShelfRenderer
                val carousel = section.musicCarouselShelfRenderer

                when {
                    shelf != null -> {
                        // Top songs shelf
                        shelf.contents?.mapNotNull { it.musicResponsiveListItemRenderer }
                            ?.forEach { item ->
                                val song = item.toSong() ?: return@forEach
                                topSongs.add(song)
                            }
                    }
                    carousel != null -> {
                        // Albums / Singles carousel
                        val carouselTitle = carousel.header
                            ?.musicCarouselShelfBasicHeaderRenderer
                            ?.title?.text() ?: "Albums"

                        val songs = carousel.contents
                            ?.mapNotNull { it.musicTwoRowItemRenderer }
                            ?.mapNotNull { it.toSong() }
                            ?: emptyList()

                        val browseId = carousel.contents
                            ?.firstOrNull()
                            ?.musicTwoRowItemRenderer
                            ?.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId

                        albums.add(YTMAlbumShelf(carouselTitle, browseId, songs))
                    }
                }
            }

            YTMArtistProfile(
                channelId = channelId,
                name = name,
                bio = bio,
                monthlyListeners = monthlyListeners,
                thumbnailUrl = thumbnailUrl,
                albums = albums,
                topSongs = topSongs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist profile for $channelId: ${e.message}", e)
            null
        }
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    /**
     * Searches YouTube Music for songs matching [query].
     * Uses the encoded params for "Songs" filter: `Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo%3D`
     */
    suspend fun searchSongs(query: String): YTMSearchResults {
        return try {
            val response = api.search(
                request = YTMSearchRequest(
                    query = query,
                    params = "Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo=" // Songs filter
                )
            )
            val songs = extractSongsFromSearch(response)
            YTMSearchResults(songs = songs)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query': ${e.message}", e)
            YTMSearchResults()
        }
    }

    /**
     * Searches YouTube Music for artists matching [query].
     * Uses the encoded params for "Artists" filter: `EgWKAQIgAWoKEAMQBBAJEAoQBQ%3D%3D`
     */
    suspend fun searchArtists(query: String): List<com.theveloper.pixelplay.data.model.Artist> {
        return try {
            val response = api.search(
                request = YTMSearchRequest(
                    query = query,
                    params = "EgWKAQIgAWoKEAMQBBAJEAoQBQ==" // Artists filter
                )
            )
            val tabs = response.contents?.tabbedSearchResultsRenderer?.tabs ?: return emptyList()
            val sections = tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents ?: return emptyList()
            val items = sections.firstOrNull()?.musicShelfRenderer?.contents ?: return emptyList()
            
            items.mapNotNull { item ->
                val renderer = item.musicResponsiveListItemRenderer ?: return@mapNotNull null
                val name = renderer.flexColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return@mapNotNull null
                val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId
                val thumbnails = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
                val imageUrl = thumbnails?.maxByOrNull { thumbnail -> thumbnail.width ?: 0 }?.url
                
                com.theveloper.pixelplay.data.model.Artist(
                    id = browseId?.hashCode()?.toLong() ?: 0L,
                    name = name,
                    songCount = 0,
                    imageUrl = imageUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search artists failed for '$query': ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fetches the user's saved/liked playlists from the YTM Library.
     */
    suspend fun getUserPlaylists(): List<com.theveloper.pixelplay.data.model.Playlist> {
        return try {
            val response = api.browse(
                request = YTMBrowseRequest(browseId = "FEmusic_liked_playlists")
            )
            
            // Just like search, playlists usually appear in the tab -> sectionList -> gridRenderer
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs ?: return emptyList()
            val sections = tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents ?: return emptyList()

            val playlists = mutableListOf<com.theveloper.pixelplay.data.model.Playlist>()
            
            for (section in sections) {
                val gridItems = section.gridRenderer?.items ?: continue
                for (item in gridItems) {
                    val twoRow = item.musicTwoRowItemRenderer ?: continue
                    val title = twoRow.title?.text() ?: "Unknown Playlist"
                    if (title == "Unknown Playlist") continue
                    
                    val browseId = twoRow.navigationEndpoint?.browseEndpoint?.browseId
                    if (browseId == null || !browseId.startsWith("VL")) continue

                    val thumbUrl = twoRow.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.bestUrl(640)

                    playlists.add(
                        com.theveloper.pixelplay.data.model.Playlist(
                            id = browseId,
                            name = title,
                            songIds = emptyList(), // we only fetch details on-demand if clicked
                            createdAt = System.currentTimeMillis(),
                            source = "YTM",
                            coverImageUri = thumbUrl
                        )
                    )
                }
            }
            playlists
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch YTM playlists: ${e.message}", e)
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Playlist Mutations
    // -------------------------------------------------------------------------

    suspend fun createPlaylist(title: String, description: String = "", videoIds: List<String> = emptyList()): String? {
        return try {
            val response = api.createPlaylist(
                request = YTMPlaylistCreateRequest(
                    title = title,
                    description = description,
                    videoIds = videoIds
                )
            )
            response.playlistId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create playlist: ${e.message}", e)
            null
        }
    }

    suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Boolean {
        // YT Music requires dropping the "VL" prefix when editing
        val cleanId = if (playlistId.startsWith("VL")) playlistId.substring(2) else playlistId
        return try {
            val response = api.editPlaylist(
                request = YTMPlaylistEditRequest(
                    playlistId = cleanId,
                    actions = listOf(
                        YTMPlaylistEditAction(
                            action = "ACTION_ADD_VIDEO",
                            addedVideoId = videoId
                        )
                    )
                )
            )
            response.status == "STATUS_SUCCEEDED"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to edit playlist: ${e.message}", e)
            false
        }
    }

    // -------------------------------------------------------------------------
    // Player / Audio Stream Metadata
    // -------------------------------------------------------------------------

    suspend fun getPlayerAudioConfig(videoId: String): AudioConfig? {
        return getPlayerRawStream(videoId)?.playerConfig?.audioConfig
    }

    suspend fun getPlayerRawStream(videoId: String): YTMPlayerResponse? {
        return try {
            // Try NewPipe Extractor first (most reliable)
            val streamUrl = newPipeExtractor.getStreamUrl(videoId)
            
            if (streamUrl != null) {
                Log.d(TAG, "NewPipe successfully extracted stream for: $videoId")
                // Create a synthetic response with the stream URL
                return YTMPlayerResponse(
                    videoDetails = VideoDetails(videoId = videoId),
                    streamingData = StreamingData(
                        adaptiveFormats = listOf(
                            AdaptiveFormat(
                                url = streamUrl,
                                mimeType = "audio/mp4",
                                bitrate = 128000 // NewPipe handles quality selection
                            )
                        )
                    )
                )
            }
            
            // Fallback to direct API (WEB_REMIX)
            Log.d(TAG, "NewPipe failed, trying WEB_REMIX API for: $videoId")
            val response = api.getPlayer(
                request = YTMPlayerRequest(videoId = videoId)
            )
            
            // If playback is blocked, try Android client as last resort
            if (response.streamingData?.adaptiveFormats.isNullOrEmpty()) {
                Log.d(TAG, "WEB_REMIX failed, trying ANDROID client for video $videoId")
                return api.getPlayer(
                    request = YTMPlayerRequest(
                        videoId = videoId,
                        context = YTMClientContext(
                            client = YTMClient(
                                clientName = "ANDROID_MUSIC",
                                clientVersion = "7.11.50",
                                platform = "MOBILE",
                                clientFormFactor = "SMALL_FORM_FACTOR"
                            )
                        )
                    )
                )
            }
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "All methods failed to fetch player stream for video $videoId: ${e.message}")
            null
        }
    }

    // -------------------------------------------------------------------------
    // Discover Feeds
    // -------------------------------------------------------------------------

    suspend fun getHomeDiscoverFeed(): List<YTMAlbumShelf> {
        return try {
            val response = api.browse(
                request = YTMBrowseRequest(browseId = "FEmusic_home")
            )
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs ?: return emptyList()
            val sections = tabs.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents ?: return emptyList()
            
            val shelves = mutableListOf<YTMAlbumShelf>()
            for (section in sections) {
                // Discover feed items are usually returned inside musicCarouselShelfRenderer
                val carousel = section.musicCarouselShelfRenderer ?: continue
                val carouselTitle = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.text() ?: "Recommendations"
                
                val songs = carousel.contents
                    ?.mapNotNull { carouselItem -> carouselItem.musicTwoRowItemRenderer }
                    ?.mapNotNull { renderer -> renderer.toSong() }
                    ?: emptyList()
                    
                if (songs.isNotEmpty()) {
                    shelves.add(YTMAlbumShelf(carouselTitle, null, songs))
                }
            }
            shelves
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get YTM home feed: ${e.message}", e)
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private fun extractSongsFromSearch(response: YTMSearchResponse): List<Song> {
        val tabs = response.contents
            ?.tabbedSearchResultsRenderer
            ?.tabs ?: return emptyList()

        return tabs.flatMap { tab ->
            val sections = tab.tabRenderer?.content?.sectionListRenderer?.contents ?: return@flatMap emptyList()
            sections.flatMap { section ->
                section.musicShelfRenderer?.contents
                    ?.mapNotNull { it.musicResponsiveListItemRenderer?.toSong() }
                    ?: emptyList()
            }
        }
    }

    /**
     * Maps a [MusicResponsiveListItemRenderer] to [Song].
     * Title is in flexColumn[0], Artist is in flexColumn[1], Album in flexColumn[2].
     */
    private fun MusicResponsiveListItemRenderer.toSong(): Song? {
        val cols = flexColumns ?: return null
        val title = cols.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.text()
            ?: return null

        val artistText = cols.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.text()
            ?: ""

        val albumText = cols.getOrNull(2)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.text()
            ?: ""

        val videoId = playlistItemData?.videoId
            ?: overlay?.musicItemThumbnailOverlayRenderer
                ?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint?.videoId
            ?: return null

        val thumbUrl = thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
            ?.maxByOrNull { it.width ?: 0 }?.url

        return Song(
            id = videoId,
            title = title,
            artist = artistText,
            artistId = -1L,
            artists = listOf(ArtistRef(id = -1L, name = artistText, isPrimary = true)),
            album = albumText,
            albumId = -1L,
            path = "", // streamed, no local path
            contentUriString = "ytmusic://$videoId",
            albumArtUriString = thumbUrl,
            duration = 0L, // resolved later from /player
            mimeType = "audio/mp4",
            bitrate = null,
            sampleRate = null,
            ytmusicId = videoId
        )
    }

    /**
     * Maps a [MusicTwoRowItemRenderer] (album carousel item) to [Song].
     * Used for artist album shelves.
     */
    private fun MusicTwoRowItemRenderer.toSong(): Song? {
        val title = this.title?.text() ?: return null
        val subtitle = this.subtitle?.text() ?: ""
        val browseId = navigationEndpoint?.browseEndpoint?.browseId ?: return null
        val thumbUrl = thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails
            ?.maxByOrNull { it.width ?: 0 }?.url

        // Albums don't have a direct videoId, so we use browseId as a placeholder ID
        return Song(
            id = browseId,
            title = title,
            artist = subtitle,
            artistId = -1L,
            album = title,
            albumId = -1L,
            path = "",
            contentUriString = "ytmusic://album/$browseId",
            albumArtUriString = thumbUrl,
            duration = 0L,
            mimeType = "audio/mp4",
            bitrate = null,
            sampleRate = null,
            ytmusicId = null // Albums don't have a videoId; songs inside do
        )
    }

    // Extension on List<Thumbnail> to pull best URL
    private fun List<Thumbnail>.bestUrl(targetWidth: Int = 1080): String? {
        val sorted = sortedByDescending { it.width ?: 0 }
        val best = sorted.firstOrNull()?.url ?: return null
        return best
            .replace(Regex("=w\\d+-h\\d+"), "=w$targetWidth-h$targetWidth")
            .replace(Regex("=s\\d+"), "=s$targetWidth")
    }
}
