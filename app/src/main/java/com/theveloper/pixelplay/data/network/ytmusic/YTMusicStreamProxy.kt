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
 * from YouTube using NewPipe extractor and proxying the audio data to ExoPlayer.
 */
@Singleton
class YTMusicStreamProxy @Inject constructor(
    private val newPipeExtractor: NewPipeYTMusicExtractor,
    okHttpClient: OkHttpClient
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

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            Timber.d("$proxyTag: Resolving stream URL for video ID: $id")
            val streamUrl = newPipeExtractor.getStreamUrl(id)
            if (streamUrl != null) {
                Timber.d("$proxyTag: Successfully resolved stream URL for $id")
            } else {
                Timber.w("$proxyTag: Failed to resolve stream URL for $id")
            }
            streamUrl
        } catch (e: Exception) {
            Timber.e(e, "$proxyTag: Error resolving stream URL for $id")
            null
        }
    }

    /**
     * Pre-fetches and caches the stream URL for a ytmusic:// URI.
     * This should be called before playback to ensure the URL is ready.
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
