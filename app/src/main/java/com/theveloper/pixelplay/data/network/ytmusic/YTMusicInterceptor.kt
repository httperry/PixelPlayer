package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "YTMusicInterceptor"

/**
 * OkHttp interceptor that:
 * 1. Injects the user's YouTube Music cookie header so all requests are authenticated.
 * 2. Injects the `Authorization: SAPISIDHASH …` header required by YouTube's inner API.
 * 3. Optionally strips tracking/telemetry fields from request bodies when the user
 *    has enabled "Disable YTM Telemetry" in Settings.
 *
 * This interceptor is only active on the YTMusicApi Retrofit client, not the global
 * OkHttpClient used for Deezer, LRCLIB, etc.
 */
class YTMusicInterceptor(
    private val cookieProvider: YTMCookieProvider,
    private val telemetryEnabled: () -> Boolean = { true }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val cookies = cookieProvider.getCookies()

        if (cookies.isNullOrBlank()) {
            Log.d(TAG, "No YTM cookies available, proceeding unauthenticated")
            return chain.proceed(originalRequest)
        }

        val sapisidHash = cookieProvider.getSapisidHash()

        val newRequest = originalRequest.newBuilder()
            .header("Cookie", cookies)
            .header("X-Goog-AuthUser", "0")
            .header("X-Origin", "https://music.youtube.com")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
            )
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "same-origin")
            .header("Sec-Fetch-Site", "same-origin")
            .apply {
                if (sapisidHash != null) {
                    header("Authorization", "SAPISIDHASH $sapisidHash")
                }
            }
            .build()

        return chain.proceed(newRequest)
    }
}

/**
 * Provides stored YTM session cookies and computes the SAPISIDHASH.
 * Implemented by [YTMSessionRepository] and injected via Hilt.
 */
interface YTMCookieProvider {
    /** Returns the raw Cookie header string (all cookies joined by "; "). */
    fun getCookies(): String?

    /**
     * Returns a precomputed SAPISIDHASH string in the format:
     *   "TIMESTAMP_BASE64SHA1(TIMESTAMP + " " + SAPISID + " " + ORIGIN)"
     * or null if no valid SAPISID is stored.
     */
    fun getSapisidHash(): String?
}
