package com.theveloper.pixelplay.data.network.ytmusic

import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming YouTube Music audio.
 *
 * Resolves `ytmusic://{videoId}` URIs by fetching streaming URLs
 * from YouTube using the Python yt-dlp backend (via WebSocket) and
 * proxying the audio data to ExoPlayer.
 *
 * This replaces the previous NewPipe Extractor-based approach. yt-dlp is:
 * - More actively maintained (handles YouTube cipher updates faster)
 * - Better at handling YouTube's evolving anti-bot measures
 * - Runs entirely in the existing Python/Chaquopy process (no extra Java dependency)
 */
@Singleton
class YTMusicStreamProxy @Inject constructor(
    okHttpClient: OkHttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes = setOf(
        "youtube.com",
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com"
    )

    // YouTube URLs expire after ~6 hours, cache for 5 hours to be safe
    override val cacheExpirationMs = 5L * 60 * 60 * 1000

    override val proxyTag = "YTMusicStreamProxy"
    override val routePath = "/ytmusic/{videoId}"
    override val routeParamName = "videoId"
    override val uriScheme = "ytmusic"
    override val routePrefix = "/ytmusic"

    override fun parseRouteParam(value: String): String? {
        // YouTube video IDs are 11 characters (alphanumeric, dash, underscore)
        return if (value.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            value
        } else {
            null
        }
    }

    override fun validateId(id: String): Boolean {
        // Validate YouTube video ID format
        return id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
    }

    override fun formatIdForUrl(id: String): String = id

    private val prefs = context.getSharedPreferences("ytm_stream_cache", android.content.Context.MODE_PRIVATE)

    init {
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        var cleaned = 0
        prefs.all.keys.filter { it.startsWith("expiry_") }.forEach { expiryKey ->
            val expiry = prefs.getLong(expiryKey, 0)
            if (now > expiry) {
                val id = expiryKey.removePrefix("expiry_")
                editor.remove("url_$id")
                editor.remove(expiryKey)
                cleaned++
            }
        }
        if (cleaned > 0) {
            editor.apply()
            Timber.d("$proxyTag: Cleaned up $cleaned expired stream URLs from cache")
        }
    }

    override suspend fun getOrFetchStreamUrl(id: String): String? {
        val cachedUrl = prefs.getString("url_$id", null)
        val expiry = prefs.getLong("expiry_$id", 0)
        
        if (cachedUrl != null && System.currentTimeMillis() < expiry) {
            Timber.d("$proxyTag: Using persistent cached stream URL for $id")
            return cachedUrl
        }
        
        val url = super.getOrFetchStreamUrl(id)
        if (url != null) {
            prefs.edit()
                .putString("url_$id", url)
                .putLong("expiry_$id", System.currentTimeMillis() + cacheExpirationMs)
                .apply()
        }
        return url
    }

    /**
     * Resolve the stream URL for a YouTube video ID via the Python yt-dlp backend.
     *
     * The WebSocket request goes to ytmusic_websocket_server.py which calls yt-dlp
     * in a thread pool executor so it doesn't block the async event loop.
     * Results are also cached server-side for 5 hours.
     */
    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            Timber.d("$proxyTag: Resolving stream URL for video ID: $id via inner tube")
            val playerResult = com.zionhuang.innertube.YouTube.player(id)
            val response = playerResult.getOrNull()
            
            val audioFormat = response?.streamingData?.adaptiveFormats
                ?.filter { it.isAudio }
                ?.maxByOrNull { it.bitrate }

            return audioFormat?.url ?: response?.streamingData?.formats?.firstOrNull()?.url
        } catch (e: Exception) {
            Timber.e(e, "$proxyTag: Error resolving stream URL for $id")
            null
        }
    }

    /**
     * Pre-fetches and caches the stream URL for a ytmusic:// URI.
     * Call this before playback to ensure the URL is ready when ExoPlayer needs it.
     *
     * @return true if the stream URL was successfully resolved and cached, false otherwise
     */
    suspend fun warmUpStreamUrl(uriString: String): Boolean {
        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme != uriScheme) return false
        val rawId = extractIdFromUri(uri) ?: return false
        val id = parseRouteParam(rawId) ?: return false
        if (!validateId(id)) return false

        // Pre-fetch the stream URL to warm up the cache
        val streamUrl = getOrFetchStreamUrl(id)
        return !streamUrl.isNullOrBlank()
    }

    /**
     * Convenience method to resolve ytmusic:// URIs to proxy URLs
     */
    fun resolveYTMusicUri(uriString: String): String? = resolveUri(uriString)
}
