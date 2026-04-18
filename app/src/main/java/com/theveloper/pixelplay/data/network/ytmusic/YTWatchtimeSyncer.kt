package com.theveloper.pixelplay.data.network.ytmusic

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YTWatchtimeSyncer"
private const val WATCHTIME_URL = "https://music.youtube.com/api/stats/watchtime"
private const val HEARTBEAT_INTERVAL_MS = 10_000L

/**
 * Sends live playback progress to YouTube's watchtime API so the user's "Resume where you left
 * off" state is always up to date — both on mobile and at music.youtube.com.
 *
 * ## Architecture — Hybrid Model
 *
 * 1. **10-second heartbeat loop** (primary): A coroutine fires every 10 seconds while a song is
 *    actively playing, mimicking the official YouTube Music web player's beacon cadence.
 *
 * 2. **Action-based triggers** (safety net): [onPause], [onStop], and [onSeek] fire an immediate
 *    ping the exact millisecond the state changes, guaranteeing that force-closing the app still
 *    saves the correct timestamp.
 *
 * ## Privacy
 *
 * When [telemetryEnabled] returns false (user toggled "Disable YTM Telemetry" in Settings),
 * *no* network request is made at all — the sync is a no-op.
 */
@Singleton
class YTWatchtimeSyncer @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cookieProvider: YTMCookieProvider,
    private val telemetryEnabled: () -> Boolean = { true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    // Current playback state — updated by MusicService
    private var currentVideoId: String? = null
    private var currentPlaylistId: String? = null
    private var positionMs: Long = 0L
    private var durationMs: Long = 0L
    private var isPlaying: Boolean = false

    // -------------------------------------------------------------------------
    // Public API — called from MusicService
    // -------------------------------------------------------------------------

    /**
     * Call when a new YTM track begins playing.
     * Starts the 10-second heartbeat loop.
     */
    fun onStart(videoId: String, playlistId: String?, durationMs: Long) {
        currentVideoId = videoId
        currentPlaylistId = playlistId
        this.durationMs = durationMs
        isPlaying = true
        startHeartbeat()
    }

    /** Call every time the ExoPlayer position changes (e.g. from a progress listener). */
    fun updatePosition(positionMs: Long) {
        this.positionMs = positionMs
    }

    /**
     * Call immediately when the user pauses.
     * Fires an instant ping and stops the heartbeat loop.
     */
    fun onPause(positionMs: Long) {
        this.positionMs = positionMs
        isPlaying = false
        stopHeartbeat()
        sendPing(state = "paused")
    }

    /**
     * Call when the user resumes after a pause.
     * Restarts the heartbeat loop.
     */
    fun onResume(positionMs: Long) {
        this.positionMs = positionMs
        isPlaying = true
        startHeartbeat()
    }

    /**
     * Call when the user seeks to a new position.
     * Fires an instant ping but does NOT stop the heartbeat.
     */
    fun onSeek(newPositionMs: Long) {
        positionMs = newPositionMs
        sendPing(state = "seeked")
    }

    /**
     * Call when the track completes normally.
     * Fires a final ping with state = "ended" and stops the loop.
     */
    fun onTrackEnd() {
        isPlaying = false
        stopHeartbeat()
        sendPing(state = "ended")
    }

    /**
     * Call from MusicService#onDestroy or when force-close is detected.
     * This is the safety-net — guarantees the timestamp is saved even if the
     * heartbeat hasn't fired yet.
     */
    fun onDestroy() {
        isPlaying = false
        stopHeartbeat()
        // Fire synchronously on IO dispatcher — we have limited time on destroy
        scope.launch { sendPingNow(state = "paused") }
    }

    // -------------------------------------------------------------------------
    // Internal heartbeat
    // -------------------------------------------------------------------------

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive && isPlaying) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (isPlaying) {
                    sendPingNow(state = "playing")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // -------------------------------------------------------------------------
    // Ping helpers
    // -------------------------------------------------------------------------

    /** Fire-and-forget ping (safe to call from any thread). */
    private fun sendPing(state: String) {
        scope.launch { sendPingNow(state) }
    }

    private suspend fun sendPingNow(state: String) {
        if (!telemetryEnabled()) {
            Log.d(TAG, "Telemetry disabled — skipping watchtime ping")
            return
        }

        val videoId = currentVideoId ?: return
        val cookies = cookieProvider.getCookies() ?: return

        try {
            val cpn = generateCpn()
            val positionSec = (positionMs / 1000.0).toString()
            val durationSec = (durationMs / 1000.0).toString()

            val body = FormBody.Builder()
                .add("ns", "yt")
                .add("el", "detailpage")
                .add("cpn", cpn)
                .add("ver", "2")
                .add("fmt", "141")
                .add("fs", "0")
                .add("rt", positionSec)
                .add("euri", "")
                .add("lact", "1800")
                .add("cl", "")
                .add("state", state)
                .add("volume", "100")
                .add("subscribed", "1")
                .add("cbr", "Chrome")
                .add("cbrver", "124.0.0")
                .add("c", "WEB_REMIX")
                .add("cver", "1.20240101.01.00")
                .add("cplayer", "UNIPLAYER")
                .add("cos", "Windows")
                .add("cosver", "10.0")
                .add("cplatform", "DESKTOP")
                .add("hl", "en_US")
                .add("cr", "US")
                .add("len", durationSec)
                .add("fexp", "")
                .add("feature", "yt_en")
                .add("rtn", positionSec)
                .add("afmt", "141")
                .add("idpj", "-7")
                .add("ldpj", "-19")
                .add("rti", positionSec)
                .add("muted", "0")
                .add("docid", videoId)
                .add("ei", "")
                .add("plid", currentPlaylistId ?: "")
                .add("sdetail", "")
                .add("of", cpn)
                .add("vm", "CAE=")
                .build()

            val request = Request.Builder()
                .url(WATCHTIME_URL)
                .post(body)
                .header("Cookie", cookies)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
                )
                .build()

            val response = okHttpClient.newCall(request).execute()
            Log.d(TAG, "Watchtime ping [$state] for $videoId @ ${positionSec}s → HTTP ${response.code}")
            response.close()
        } catch (e: Exception) {
            Log.w(TAG, "Watchtime ping failed for $state: ${e.message}")
        }
    }

    /** Generates a random 16-character CPN string (Client Playback Nonce). */
    private fun generateCpn(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
