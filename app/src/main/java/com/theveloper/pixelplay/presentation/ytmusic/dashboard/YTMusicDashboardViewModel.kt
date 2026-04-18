package com.theveloper.pixelplay.presentation.ytmusic.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadHomeFeed()
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            _homeFeed.value = repository.getHomeDiscoverFeed()
            _isLoading.value = false
        }
    }
}
