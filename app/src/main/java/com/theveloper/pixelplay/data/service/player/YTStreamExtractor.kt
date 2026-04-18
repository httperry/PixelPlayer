package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YTStreamExtractor"

/**
 * Preferred audio itag priority order (highest quality first):
 *  141 → 256 kbps AAC (Premium)
 *  251 → 160 kbps Opus (free tier)
 *  140 → 128 kbps AAC (free tier)
 */
private val PREFERRED_ITAGS = listOf(141, 251, 140, 139)

data class YTMStream(
    val url: String,
    val itag: Int,
    val mimeType: String,
    val bitrateKbps: Int,
    /** Human-readable label e.g. "256kbps AAC (Premium)" */
    val qualityLabel: String
)

@Singleton
class YTStreamExtractor @Inject constructor(
    private val ytMusicApi: YTMusicApi
) {

    /**
     * Resolves the best available audio stream for [videoId].
     *
     * Returns [YTMStream] containing the direct CDN URL and quality metadata,
     * or null if the player response contains no usable audio formats.
     *
     * @param videoId The YouTube video (song) ID, e.g. "dQw4w9WgXcQ"
     */
    suspend fun resolveStream(videoId: String): YTMStream? {
        return try {
            val response = ytMusicApi.getPlayer(
                request = YTMPlayerRequest(videoId = videoId)
            )

            val formats = response.streamingData?.adaptiveFormats
            if (formats.isNullOrEmpty()) {
                Log.w(TAG, "No adaptive formats found for videoId=$videoId")
                return null
            }

            // Filter to audio-only formats and sort by our preference order
            val audioFormats = formats.filter { it.mimeType.startsWith("audio/") }
            Log.d(TAG, "Available audio formats: ${audioFormats.map { it.itag }}")

            val best = PREFERRED_ITAGS
                .firstNotNullOfOrNull { preferredItag ->
                    audioFormats.firstOrNull { it.itag == preferredItag && !it.url.isNullOrBlank() }
                }
                ?: audioFormats.maxByOrNull { it.bitrate }  // fallback: highest bitrate

            if (best == null || best.url.isNullOrBlank()) {
                Log.w(TAG, "No suitable audio stream found for videoId=$videoId")
                return null
            }

            val bitrateKbps = best.bitrate / 1000
            val qualityLabel = buildQualityLabel(best.itag, bitrateKbps, best.mimeType)
            Log.d(TAG, "Resolved stream for $videoId → itag=${best.itag} ($qualityLabel)")

            YTMStream(
                url = best.url,
                itag = best.itag,
                mimeType = best.mimeType,
                bitrateKbps = bitrateKbps,
                qualityLabel = qualityLabel
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for videoId=$videoId: ${e.message}", e)
            null
        }
    }

    /**
     * Convenience overload that uses [Song.ytmusicId] as the video ID.
     * Returns null immediately if the song has no ytmusicId.
     */
    suspend fun resolveStream(song: Song): YTMStream? {
        val videoId = song.ytmusicId ?: return null
        return resolveStream(videoId)
    }

    private fun buildQualityLabel(itag: Int, bitrateKbps: Int, mimeType: String): String {
        val codec = when {
            mimeType.contains("opus", ignoreCase = true) -> "Opus"
            mimeType.contains("mp4a", ignoreCase = true) -> "AAC"
            else -> mimeType.substringAfter("audio/").substringBefore(";")
        }
        val premiumSuffix = if (itag == 141) " (Premium)" else ""
        return "${bitrateKbps}kbps $codec$premiumSuffix"
    }
}
