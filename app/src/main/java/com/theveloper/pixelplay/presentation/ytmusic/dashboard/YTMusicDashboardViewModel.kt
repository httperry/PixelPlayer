package com.theveloper.pixelplay.presentation.ytmusic.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.network.ytmusic.YTMAlbumShelf
import com.theveloper.pixelplay.data.network.ytmusic.YTMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YTMusicDashboardViewModel @Inject constructor(
    private val repository: YTMusicRepository
) : ViewModel() {

    private val _homeFeed = MutableStateFlow<List<YTMAlbumShelf>>(emptyList())
    val homeFeed: StateFlow<List<YTMAlbumShelf>> = _homeFeed.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadHomeFeed()
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // 1. Load cached data instantly to prevent freezing/blank screen
            val cachedHome = repository.getCachedHomeDiscoverFeed()
            if (cachedHome != null && cachedHome.isNotEmpty()) {
                _homeFeed.value = cachedHome
            }
            
            val cachedRecent = repository.getCachedRecentlyPlayed()
            if (cachedRecent != null && cachedRecent.isNotEmpty()) {
                _recentlyPlayed.value = cachedRecent
            }

            // 2. Fetch fresh data in the background and update
            val freshHome = repository.getHomeDiscoverFeed()
            if (freshHome.isNotEmpty()) {
                _homeFeed.value = freshHome
            }
            
            val freshRecent = repository.getRecentlyPlayed()
            if (freshRecent.isNotEmpty()) {
                _recentlyPlayed.value = freshRecent
            }
            
            _isLoading.value = false
        }
    }
}
