package com.theveloper.pixelplay.presentation.viewmodel

import android.util.Log
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import com.theveloper.pixelplay.data.network.ytmusic.YTMusicRepository
import com.theveloper.pixelplay.data.network.ytmusic.YTMSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 *
 * YTMusic search uses progressive loading:
 * 1. Local library results are shown immediately
 * 2. First 10 YTMusic songs are emitted as soon as the first batch arrives
 * 3. Next 10 songs arrive in the background (second emission from searchSongsFlow)
 *
 * [isYtmSearching] is set to true as soon as a query is typed and reset to false
 * when all YTMusic batches are done, enabling the UI to show skeleton rows instead
 * of the "no results found" empty state during the loading window.
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val ytSessionRepository: YTMSessionRepository
) {
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private data class SearchRequest(
        val query: String,
        val requestId: Long
    )

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    /**
     * True while a YTMusic search is in-flight.
     * The UI shows skeleton/shimmer rows when this is true and results are empty,
     * instead of flashing the "no results found" state.
     */
    private val _isYtmSearching = MutableStateFlow(false)
    val isYtmSearching = _isYtmSearching.asStateFlow()

    private val fullSearchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var fullSearchJob: Job? = null
    private var forcedSearchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeSearchRequests() {
        fullSearchJob?.cancel()
        fullSearchJob = scope?.launch {
            fullSearchRequests
                .debounce(300L) // Fast debounce for instant rich search
                .collectLatest { request ->
                    executeFullSearchInternal(request)
                }
        }
    }

    private suspend fun executeFullSearchInternal(request: SearchRequest) {
        val normalizedQuery = request.query

        if (normalizedQuery.isBlank()) {
            _isYtmSearching.value = false
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
            return
        }

        try {

                        val currentFilter = _selectedSearchFilter.value

                        // ── Step 1: Signal that YTMusic search is in progress ────────────
                        // This lets the UI show skeleton rows immediately instead of the
                        // "no results found" empty state.
                        _isYtmSearching.value = true

                        // ── Step 2: Show local results immediately (fast path) ────────────
                        val baseResults = withContext(Dispatchers.IO) {
                            musicRepository.searchAll(normalizedQuery, currentFilter).first()
                        }

                        // Check if this request is still valid
                        if (request.requestId != latestSearchRequestId.get()) {
                            _isYtmSearching.value = false
                            return
                        }

                        // Emit local results right away so UI is responsive
                        _searchResults.value = baseResults.toImmutableList()

                        // ── Step 3: Fetch YTMusic results progressively ───────────────────
                        val cookies = withContext(Dispatchers.IO) {
                            ytSessionRepository.getCookies()
                        }

                        if (!cookies.isNullOrBlank()) {
                            // Launch artist search in parallel — it doesn't benefit from
                            // progressive loading the same way songs do.
                            val ytArtistsDeferred = scope?.async(Dispatchers.IO) {
                                if (currentFilter == SearchFilterType.ALL || currentFilter == SearchFilterType.ARTISTS)
                                    ytMusicRepository.searchArtists(normalizedQuery)
                                else emptyList()
                            }

                            if (currentFilter == SearchFilterType.ALL || currentFilter == SearchFilterType.SONGS) {
                                // Collect each emission from the progressive flow:
                                // 1st emission: first 10 songs
                                // 2nd emission: all 20 songs
                                ytMusicRepository.searchSongsFlow(normalizedQuery)
                                    .collect { ytSearchResult ->
                                        // Guard against superseded queries
                                        if (request.requestId != latestSearchRequestId.get()) {
                                            return@collect
                                        }

                                        val ytSongs = ytSearchResult.songs.map { SearchResultItem.SongItem(it) }

                                        // Merge local + YTM songs + artists (artists arrive when available)
                                        val ytArtists = if (!ytSearchResult.hasMore) {
                                            // All song batches done — now we can include artist results
                                            ytArtistsDeferred?.await()
                                                ?.map { SearchResultItem.ArtistItem(it) }
                                                ?: emptyList()
                                        } else {
                                            emptyList() // Still loading songs, skip artists for now
                                        }

                                        Log.d("SearchStateHolder", "YTMusic batch for '$normalizedQuery': songs=${ytSongs.size} hasMore=${ytSearchResult.hasMore}")

                                        val merged = baseResults.toMutableList()
                                        merged.addAll(0, ytArtists)
                                        merged.addAll(0, ytSongs)

                                        _searchResults.value = merged.toImmutableList()

                                        // Stop showing skeleton once we have the first batch
                                        if (ytSongs.isNotEmpty() || !ytSearchResult.hasMore) {
                                            _isYtmSearching.value = false
                                        }
                                    }
                            } else {
                                // Non-songs filter: just fetch artists
                                val ytArtists = ytArtistsDeferred?.await()
                                    ?.map { SearchResultItem.ArtistItem(it) }
                                    ?: emptyList()

                                if (request.requestId == latestSearchRequestId.get()) {
                                    val merged = baseResults.toMutableList()
                                    merged.addAll(0, ytArtists)
                                    _searchResults.value = merged.toImmutableList()
                                }
                            }
                        }

                        // Always clear loading when done
                        _isYtmSearching.value = false

                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                        _isYtmSearching.value = false
                    } catch (e: Exception) {
                        _isYtmSearching.value = false
                        if (request.requestId == latestSearchRequestId.get()) {
                            Log.e("SearchStateHolder", "Error performing search for query: $normalizedQuery", e)
                            // Keep existing results instead of clearing them
                            if (_searchResults.value.isEmpty()) {
                                _searchResults.value = persistentListOf()
                            }
                        }
                    }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Log.e("SearchStateHolder", "Error loading search history", e)
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Log.e("SearchStateHolder", "Error adding search history item", e)
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        val normalizedQuery = query.trim()
        val requestId = latestSearchRequestId.incrementAndGet()

        if (normalizedQuery.isBlank()) {
            _isYtmSearching.value = false
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        val request = SearchRequest(normalizedQuery, requestId)
        fullSearchRequests.tryEmit(request)
    }

    fun forceFullSearch(query: String) {
        val normalizedQuery = query.trim()
        val requestId = latestSearchRequestId.incrementAndGet()
        
        // Immediately cancel pending debounced searches
        fullSearchJob?.cancel()
        forcedSearchJob?.cancel()

        forcedSearchJob = scope?.launch {
            executeFullSearchInternal(SearchRequest(normalizedQuery, requestId))
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Log.e("SearchStateHolder", "Error deleting search history item", e)
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Log.e("SearchStateHolder", "Error clearing search history", e)
            }
        }
    }

    fun onCleared() {
        fullSearchJob?.cancel()
        forcedSearchJob?.cancel()
        scope = null
    }
}
