package com.theveloper.pixelplay.data

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.horologist.audio.BluetoothSettings.launchBluetoothSettings
import com.google.android.horologist.audio.OutputSwitcher.launchSystemMediaOutputSwitcherUi
import com.theveloper.pixelplay.shared.WearVolumeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides local (watch-side) STREAM_MUSIC volume state and controls.
 */
@Singleton
class WearVolumeRepository @Inject constructor(
    private val application: Application,
) {
    private val audioManager by lazy {
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val mediaRouter by lazy { MediaRouter.getInstance(application) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val watchRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
        .build()
    private var discoveryEnabled = false

    private val _watchVolumeState = MutableStateFlow(readFallbackVolumeState())
    val watchVolumeState: StateFlow<WearVolumeState> = _watchVolumeState.asStateFlow()

    private val _watchAudioRoutes = MutableStateFlow<List<WearAudioOutputRoute>>(emptyList())
    val watchAudioRoutes: StateFlow<List<WearAudioOutputRoute>> = _watchAudioRoutes.asStateFlow()

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = syncWatchAudioState()

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = syncWatchAudioState()

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = syncWatchAudioState()

        override fun onRouteSelected(
            router: MediaRouter,
            route: MediaRouter.RouteInfo,
            reason: Int,
        ) = syncWatchAudioState()

        override fun onRouteConnected(
            router: MediaRouter,
            connectedRoute: MediaRouter.RouteInfo,
            requestedRoute: MediaRouter.RouteInfo,
        ) = syncWatchAudioState()

        override fun onRouteDisconnected(
            router: MediaRouter,
            disconnectedRoute: MediaRouter.RouteInfo?,
            requestedRoute: MediaRouter.RouteInfo,
            reason: Int,
        ) = syncWatchAudioState()

        override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = syncWatchAudioState()
    }

    init {
        scope.launch {
            registerRouteCallback()
            syncWatchAudioState()
        }
    }

    fun refreshWatchVolumeState() {
        syncWatchAudioState()
    }

    fun volumeUpOnWatch() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            0,
        )
        refreshWatchVolumeState()
    }

    fun volumeDownOnWatch() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            0,
        )
        refreshWatchVolumeState()
    }

    fun selectWatchAudioRoute(routeId: String) {
        scope.launch {
            val route = mediaRouter.routes.firstOrNull { candidate ->
                candidate.isWatchAudioRoute() && candidate.id == routeId
            } ?: run {
                launchWatchAudioOutputPicker()
                return@launch
            }

            if (!route.isSelected()) {
                route.select()
            }
            syncWatchAudioState()
        }
    }

    fun launchWatchAudioOutputPicker(closeOnConnect: Boolean = true) {
        scope.launch {
            if (!application.launchSystemMediaOutputSwitcherUi(application.packageName)) {
                application.launchBluetoothSettings(closeOnConnect)
            }
        }
    }

    fun setWatchRouteDiscoveryEnabled(enabled: Boolean) {
        scope.launch {
            if (discoveryEnabled == enabled) return@launch
            discoveryEnabled = enabled
            registerRouteCallback()
        }
    }

    private fun syncWatchAudioState() {
        scope.launch {
            val routes = mediaRouter.routes
                .filter { route -> route.isWatchAudioRoute() && route.isEnabled }
                .map { route -> route.toWearAudioOutputRoute() }
                .sortedWith(
                    compareByDescending<WearAudioOutputRoute> { it.isSelected }
                        .thenByDescending { it.isBluetooth }
                        .thenByDescending { it.isConnected }
                        .thenBy { it.name.lowercase() }
                )

            _watchAudioRoutes.value = routes

            val selectedRoute = routes.firstOrNull { it.isSelected }
                ?: mediaRouter.selectedRoute
                    .takeIf { route -> route.isWatchAudioRoute() }
                    ?.toWearAudioOutputRoute()

            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
            val level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)

            _watchVolumeState.value = WearVolumeState(
                level = level,
                max = max,
                routeType = selectedRoute?.routeType ?: WearVolumeState.ROUTE_TYPE_WATCH,
                routeName = selectedRoute?.name.orEmpty().ifBlank { DEFAULT_WATCH_ROUTE_NAME },
            )
        }
    }

    private fun readFallbackVolumeState(): WearVolumeState {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return WearVolumeState(
            level = level.coerceIn(0, max.coerceAtLeast(0)),
            max = max.coerceAtLeast(0),
            routeType = WearVolumeState.ROUTE_TYPE_WATCH,
            routeName = DEFAULT_WATCH_ROUTE_NAME,
        )
    }

    companion object {
        private const val DEFAULT_WATCH_ROUTE_NAME = "Watch speaker"
    }

    private fun registerRouteCallback() {
        mediaRouter.removeCallback(routeCallback)
        mediaRouter.addCallback(
            watchRouteSelector,
            routeCallback,
            if (discoveryEnabled) {
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            } else {
                0
            },
        )
    }
}

private fun MediaRouter.RouteInfo.isWatchAudioRoute(): Boolean {
    return isSystemRoute && (isBluetooth || isDeviceSpeaker)
}

private fun MediaRouter.RouteInfo.toWearAudioOutputRoute(): WearAudioOutputRoute {
    val routeType = if (isBluetooth) {
        WearVolumeState.ROUTE_TYPE_BLUETOOTH
    } else {
        WearVolumeState.ROUTE_TYPE_WATCH
    }
    val displayName = when {
        isBluetooth -> name.ifBlank { "Bluetooth" }
        else -> name.ifBlank { "Watch speaker" }
    }
    val resolvedConnectionState = if (isDeviceSpeaker) {
        MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
    } else {
        connectionState
    }

    return WearAudioOutputRoute(
        id = id,
        name = displayName,
        routeType = routeType,
        connectionState = resolvedConnectionState,
        isSelected = isSelected(),
    )
}
