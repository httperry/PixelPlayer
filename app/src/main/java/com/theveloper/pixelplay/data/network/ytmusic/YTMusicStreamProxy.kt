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
    private val sessionRepo: YTMSessionRepository,
    private val poTokenGenerator: YouTubePoTokenGenerator,
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


    override suspend fun customHeaders(): Map<String, String> {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
        val cookie = sessionRepo.getCookies() ?: ""
        
        // Ensure poToken is generated
        val visitorData = com.zionhuang.innertube.YouTube.visitorData
        val poToken = poTokenGenerator.generatePoToken(visitorData) ?: ""
        
        val map = mutableMapOf(
            "User-Agent" to userAgent,
            "Connection" to "keep-alive"
        )
        if (cookie.isNotBlank()) map["Cookie"] = cookie
        if (poToken.isNotBlank()) map["X-Goog-PoToken"] = poToken
        if (visitorData.isNotBlank()) map["X-Goog-Visitor-Id"] = visitorData
        
        return map
    }

    override fun parseRouteParam(value: String): String? {
        // YouTube video IDs are 11 characters (alphanumeric, dash, underscore)
        return if (value.matches(Regex("^[a-zA-Z0-9_-]{10,15}$"))) {
            value
        } else {
            null
        }
    }

    override fun validateId(id: String): Boolean {
        // Validate YouTube video ID format
        return id.matches(Regex("^[a-zA-Z0-9_-]{10,15}$"))
    }

    override fun formatIdForUrl(id: String): String = id

    private val prefs = context.getSharedPreferences("ytm_stream_cache_v3", android.content.Context.MODE_PRIVATE)

    init {
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        var cleaned = 0
        prefs.all.keys.filter { it.startsWith("expiry_") }.forEach { expiryKey ->
            val expiry = prefs.getLong(expiryKey, 0)
            val id = expiryKey.removePrefix("expiry_")
            val cachedUrl = prefs.getString("url_$id", null)
            // Evict if expired OR if the stored URL is poisoned (sig=null from a broken session)
            if (now > expiry || (cachedUrl != null && isUrlPoisoned(cachedUrl))) {
                editor.remove("url_$id")
                editor.remove(expiryKey)
                cleaned++
            }
        }
        if (cleaned > 0) {
            editor.apply()
            Timber.d("$proxyTag: Cleaned up $cleaned expired/invalid stream URLs from cache")
        }
    }

    override suspend fun getOrFetchStreamUrl(id: String): String? {
        val cachedUrl = prefs.getString("url_$id", null)
        val expiry = prefs.getLong("expiry_$id", 0)

        if (cachedUrl != null && System.currentTimeMillis() < expiry) {
            // Sanity-check: reject cached URLs that were stored while signature/nsig
            // decoding was broken (e.g. &sig=null, &signature=null).
            // These cause 403 Forbidden and must be re-resolved fresh.
            if (isUrlPoisoned(cachedUrl)) {
                Timber.w("$proxyTag: Evicting poisoned cached URL for $id (sig=null or invalid)")
                prefs.edit().remove("url_$id").remove("expiry_$id").apply()
            } else {
                Timber.d("$proxyTag: Using persistent cached stream URL for $id")
                return cachedUrl
            }
        }

        val url = super.getOrFetchStreamUrl(id)
        if (url != null) {
            if (isUrlPoisoned(url)) {
                // Never cache a broken URL — it would just poison future sessions.
                Timber.w("$proxyTag: Resolved URL for $id is poisoned (sig=null), not caching")
            } else {
                prefs.edit()
                    .putString("url_$id", url)
                    .putLong("expiry_$id", System.currentTimeMillis() + cacheExpirationMs)
                    .apply()
            }
        }
        return url
    }

    /**
     * Returns true if a stream URL contains markers of a failed signature/nsig decode.
     * Such URLs result in 403 Forbidden from Google's video servers.
     *
     * Known poison indicators:
     *  - `sig=null` or `signature=null`  — signature JS evaluation returned null
     *  - `n=null`                         — nsig JS evaluation returned null
     *  - URL is suspiciously short         — incomplete resolve
     */
    private fun isUrlPoisoned(url: String): Boolean {
        return url.contains("sig=null", ignoreCase = true) ||
               url.contains("signature=null", ignoreCase = true) ||
               url.contains("&n=null", ignoreCase = true) ||
               url.length < 200
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
            val visitorData = com.zionhuang.innertube.YouTube.visitorData
            var poToken = ""
            for (attempt in 1..3) {
                poToken = try {
                    kotlinx.coroutines.withTimeoutOrNull(10000) {
                        poTokenGenerator.generatePoToken(visitorData)
                    } ?: ""
                } catch (e: Exception) {
                    Timber.e(e, "$proxyTag: Error generating poToken on attempt $attempt")
                    ""
                }
                
                if (poToken.isNotBlank()) {
                    Timber.d("$proxyTag: Successfully generated poToken on attempt $attempt")
                    break
                } else if (attempt < 3) {
                    Timber.w("$proxyTag: Failed to generate poToken (Attempt $attempt). Retrying...")
                    kotlinx.coroutines.delay(1000) // Small backoff before retrying
                } else {
                    Timber.e("$proxyTag: Failed to generate poToken after 3 attempts.")
                }
            }
            val playerResult = com.zionhuang.innertube.YouTube.player(id, poToken = poToken)
            val response = playerResult.getOrNull()
            
            val adaptiveFormats = response?.streamingData?.adaptiveFormats.orEmpty()

            // Filter to audio-only formats (width == null means no video track)
            val audioOnlyFormats = adaptiveFormats.filter { it.isAudio || it.mimeType.startsWith("audio/") }

            // 1. Prefer highest-bitrate Opus/WebM — YouTube Premium delivers these at up to 256 kbps
            val opusFormat = audioOnlyFormats
                .filter { it.mimeType.contains("opus") || it.mimeType.contains("webm") }
                .maxByOrNull { it.bitrate }

            // 2. Fall back to any highest-bitrate audio-only format (AAC/MP4 etc.)
            val bestAudioFormat = opusFormat ?: audioOnlyFormats.maxByOrNull { it.bitrate }

            val selectedUrl = bestAudioFormat?.url
                ?: response?.streamingData?.formats?.firstOrNull { it.url != null }?.url

            if (audioOnlyFormats.isNotEmpty() && selectedUrl == null) {
                Timber.w("$proxyTag: Audio formats found but all have null URLs for $id — possible nsig/signature decoding issue")
            } else if (selectedUrl != null) {
                val fmt = bestAudioFormat
                Timber.d("$proxyTag: Selected format for $id — mimeType=${fmt?.mimeType} bitrate=${fmt?.bitrate} audioQuality=${fmt?.audioQuality}")
            }

            return selectedUrl
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
        if (uri.scheme != uriScheme) {
            Timber.e("$proxyTag: scheme mismatch ${uri.scheme} != $uriScheme")
            return false
        }
        val rawId = extractIdFromUri(uri)
        if (rawId == null) {
            Timber.e("$proxyTag: rawId is null for $uriString")
            return false
        }
        val id = parseRouteParam(rawId)
        if (id == null) {
            Timber.e("$proxyTag: parseRouteParam returned null for rawId=$rawId")
            return false
        }
        if (!validateId(id)) {
            Timber.e("$proxyTag: validateId failed for id=$id")
            return false
        }

        // Pre-fetch the stream URL to warm up the cache
        val streamUrl = getOrFetchStreamUrl(id)
        if (streamUrl.isNullOrBlank()) {
            Timber.e("$proxyTag: getOrFetchStreamUrl returned null or blank for id=$id")
            return false
        }
        return true
    }

    /**
     * Convenience method to resolve ytmusic:// URIs to proxy URLs
     */
    fun resolveYTMusicUri(uriString: String): String? = resolveUri(uriString)
}
