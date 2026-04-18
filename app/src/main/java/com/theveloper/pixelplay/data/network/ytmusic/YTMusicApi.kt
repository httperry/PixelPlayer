package com.theveloper.pixelplay.data.network.ytmusic

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

// ---------------------------------------------------------------------------
// /playlist/create & /browse/edit_playlist — Playlist Management
// ---------------------------------------------------------------------------
data class YTMPlaylistCreateRequest(
    val context: YTMContext = YTMContext(),
    val title: String,
    val description: String = "",
    val privacyStatus: String = "PRIVATE",
    val videoIds: List<String> = emptyList()
)

data class YTMPlaylistCreateResponse(
    val playlistId: String? = null
)

data class YTMPlaylistEditRequest(
    val context: YTMContext = YTMContext(),
    val playlistId: String,
    val actions: List<YTMPlaylistEditAction>
)

data class YTMPlaylistEditAction(
    val action: String, // "ACTION_ADD_VIDEO", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID"
    val addedVideoId: String? = null,
    val removedVideoId: String? = null
)

data class YTMPlaylistEditResponse(
    val status: String? = null
)

// ---------------------------------------------------------------------------
// Shared inner-tube request context (injected by YTMusicInterceptor)
// ---------------------------------------------------------------------------
data class YTMContext(
    val context: YTMClientContext = YTMClientContext()
)

data class YTMClientContext(
    val client: YTMClient = YTMClient()
)

data class YTMClient(
    val clientName: String = "WEB_REMIX",
    val clientVersion: String = "1.20240101.01.00",
    val hl: String = "en",
    val gl: String = "US"
)

// ---------------------------------------------------------------------------
// /player — Stream URL resolution
// ---------------------------------------------------------------------------
data class YTMPlayerRequest(
    val videoId: String,
    val context: YTMClientContext = YTMClientContext(),
    val playbackContext: PlaybackContext = PlaybackContext()
)

data class PlaybackContext(
    val contentPlaybackContext: ContentPlaybackContext = ContentPlaybackContext()
)

data class ContentPlaybackContext(
    val signatureTimestamp: Int = 19950 // kept in sync with YouTube's player JS
)

data class YTMPlayerResponse(
    val videoDetails: VideoDetails? = null,
    val streamingData: StreamingData? = null,
    val playerConfig: PlayerConfig? = null
)

data class PlayerConfig(
    val audioConfig: AudioConfig? = null
)

data class AudioConfig(
    val loudnessDb: Float? = null,
    val perceptualLoudnessDb: Float? = null
)

data class VideoDetails(
    val videoId: String = "",
    val title: String = "",
    val author: String = "",
    val thumbnail: ThumbnailList? = null,
    val lengthSeconds: String = "0"
)

data class StreamingData(
    val adaptiveFormats: List<AdaptiveFormat>? = null
)

data class AdaptiveFormat(
    val itag: Int = 0,
    val url: String? = null,
    val mimeType: String = "",
    val bitrate: Int = 0,
    val audioQuality: String? = null,
    val approxDurationMs: String? = null
)

// ---------------------------------------------------------------------------
// /browse — Home feed, Artist pages, Library
// ---------------------------------------------------------------------------
data class YTMBrowseRequest(
    val browseId: String,
    val context: YTMClientContext = YTMClientContext(),
    val params: String? = null
)

data class YTMArtistBrowseResponse(
    val header: ArtistHeader? = null,
    val contents: ArtistContents? = null
)

data class ArtistHeader(
    val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer? = null
)

data class MusicImmersiveHeaderRenderer(
    val title: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailList? = null,
    val subscriptionButton: SubscriptionButton? = null
)

data class SubscriptionButton(
    val subscribeButtonRenderer: SubscribeButtonRenderer? = null
)

data class SubscribeButtonRenderer(
    val subscriberCountText: Runs? = null
)

data class ArtistContents(
    val singleColumnBrowseResultsRenderer: SingleColumnBrowseResultsRenderer? = null
)

data class SingleColumnBrowseResultsRenderer(
    val tabs: List<TabWrapper>? = null
)

data class TabWrapper(
    val tabRenderer: TabRenderer? = null
)

data class TabRenderer(
    val content: TabContent? = null
)

data class TabContent(
    val sectionListRenderer: SectionListRenderer? = null
)

data class SectionListRenderer(
    val contents: List<SectionContent>? = null
)

data class SectionContent(
    val musicShelfRenderer: MusicShelfRenderer? = null,
    val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null,
    val gridRenderer: GridRenderer? = null
)

data class GridRenderer(
    val items: List<GridItem>? = null
)

data class GridItem(
    val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null
)

data class MusicShelfRenderer(
    val title: Runs? = null,
    val contents: List<MusicShelfItem>? = null
)

data class MusicCarouselShelfRenderer(
    val header: CarouselHeader? = null,
    val contents: List<MusicCarouselItem>? = null
)

data class CarouselHeader(
    val musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRenderer? = null
)

data class MusicCarouselShelfBasicHeaderRenderer(
    val title: Runs? = null
)

data class MusicShelfItem(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

data class MusicCarouselItem(
    val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null
)

data class MusicTwoRowItemRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val thumbnailRenderer: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val overlay: Overlay? = null,
    val playlistItemData: PlaylistItemData? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

data class Overlay(
    val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer? = null
)

data class MusicItemThumbnailOverlayRenderer(
    val content: OverlayContent? = null
)

data class OverlayContent(
    val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null
)

data class MusicPlayButtonRenderer(
    val playNavigationEndpoint: NavigationEndpoint? = null
)

data class PlaylistItemData(
    val videoId: String? = null
)

data class FlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer? = null
)

data class MusicResponsiveListItemFlexColumnRenderer(
    val text: Runs? = null
)

data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null
)

data class MusicThumbnailRenderer(
    val thumbnail: ThumbnailList? = null
)

data class ThumbnailList(
    val thumbnails: List<Thumbnail>? = null
) {
    /** Returns highest-resolution available thumbnail URL, optionally upscaling to 1080px. */
    fun bestUrl(targetWidth: Int = 1080): String? {
        val sorted = thumbnails?.sortedByDescending { (it.width ?: 0) } ?: return null
        val best = sorted.firstOrNull()?.url ?: return null
        // Replace any embedded size suffix so we always get max resolution
        return best
            .replace(Regex("=w\\d+-h\\d+"), "=w$targetWidth-h$targetWidth")
            .replace(Regex("=s\\d+"), "=s$targetWidth")
    }
}

data class Thumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null
)

data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null
)

data class BrowseEndpoint(
    val browseId: String? = null
)

data class Runs(
    val runs: List<Run>? = null
) {
    fun text(): String = runs?.joinToString("") { it.text ?: "" } ?: ""
}

data class Run(
    val text: String? = null,
    val navigationEndpoint: NavigationEndpoint? = null
)

// ---------------------------------------------------------------------------
// /search — Song, album, and artist search
// ---------------------------------------------------------------------------
data class YTMSearchRequest(
    val query: String,
    val params: String? = null, // encoded filter for songs/albums/artists
    val context: YTMClientContext = YTMClientContext()
)

data class YTMSearchResponse(
    val contents: SearchContents? = null
)

data class SearchContents(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null
)

data class TabbedSearchResultsRenderer(
    val tabs: List<TabWrapper>? = null
)

// ---------------------------------------------------------------------------
// /next — Lyrics, queue, related tracks
// ---------------------------------------------------------------------------
data class YTMNextRequest(
    val videoId: String,
    val playlistId: String? = null,
    val context: YTMClientContext = YTMClientContext()
)

data class YTMNextResponse(
    val contents: NextContents? = null
)

data class NextContents(
    val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer? = null
)

data class SingleColumnMusicWatchNextResultsRenderer(
    val tabbedRenderer: TabbedRenderer? = null
)

data class TabbedRenderer(
    val watchNextTabbedResultsRenderer: WatchNextTabbedResultsRenderer? = null
)

data class WatchNextTabbedResultsRenderer(
    val tabs: List<TabWrapper>? = null
)

// ---------------------------------------------------------------------------
// /music/get_lyrics — Synced lyrics
// ---------------------------------------------------------------------------
data class YTMLyricsRequest(
    val browseId: String, // derived from /next response
    val context: YTMClientContext = YTMClientContext()
)

data class YTMLyricsResponse(
    val contents: LyricsContents? = null
)

data class LyricsContents(
    val sectionListRenderer: SectionListRenderer? = null
)

// ---------------------------------------------------------------------------
// Retrofit Service Interface
// ---------------------------------------------------------------------------
interface YTMusicApi {

    /** Resolve a song's Premium stream URL and metadata. */
    @POST("youtubei/v1/player")
    @Headers("Content-Type: application/json")
    suspend fun getPlayer(
        @Body request: YTMPlayerRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMPlayerResponse

    /**
     * Browse a page — used for:
     *  - Home feed (browseId = "FEmusic_home")
     *  - Artist page (browseId = "UC…")
     *  - Library playlists (browseId = "FEmusic_liked_playlists")
     */
    @POST("youtubei/v1/browse")
    @Headers("Content-Type: application/json")
    suspend fun browse(
        @Body request: YTMBrowseRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMArtistBrowseResponse

    /** Full-text search across songs, albums, and artists. */
    @POST("youtubei/v1/search")
    @Headers("Content-Type: application/json")
    suspend fun search(
        @Body request: YTMSearchRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMSearchResponse

    /** Fetch related content, queue, and lyrics browse-ID for a given video. */
    @POST("youtubei/v1/next")
    @Headers("Content-Type: application/json")
    suspend fun next(
        @Body request: YTMNextRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMNextResponse

    /** Fetch synced lyrics for a song using the browseId from /next. */
    @POST("youtubei/v1/browse")
    @Headers("Content-Type: application/json")
    suspend fun getLyrics(
        @Body request: YTMLyricsRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMLyricsResponse

    /** Creates a new playlist on the user's YouTube Music account. */
    @POST("youtubei/v1/playlist/create")
    @Headers("Content-Type: application/json")
    suspend fun createPlaylist(
        @Body request: YTMPlaylistCreateRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMPlaylistCreateResponse

    /** Edits a user's playlist on YouTube Music (e.g. adding songs). */
    @POST("youtubei/v1/browse/edit_playlist")
    @Headers("Content-Type: application/json")
    suspend fun editPlaylist(
        @Body request: YTMPlaylistEditRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMPlaylistEditResponse

    /** Resolves stream URLs and audio configuration (loudness) for a video. */
    @POST("youtubei/v1/player")
    @Headers("Content-Type: application/json")
    suspend fun getPlayer(
        @Body request: YTMPlayerRequest,
        @Query("key") key: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    ): YTMPlayerResponse
}
