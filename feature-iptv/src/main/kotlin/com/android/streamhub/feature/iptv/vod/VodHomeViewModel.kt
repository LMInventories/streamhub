package com.android.streamhub.feature.iptv.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.IptvSourceConfig
import com.android.streamhub.feature.iptv.data.IptvSourceConfigRepository
import com.android.streamhub.feature.iptv.data.IptvVodRepository
import com.android.streamhub.feature.iptv.data.VodMovieInfo
import com.android.streamhub.feature.iptv.data.VodShowInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodHomeUiState(
    // Same reasoning as VodLibraryUiState's matching fields - defaults true so the first frame
    // shows the loading spinner rather than a flash of the "add playlist" prompt.
    val hasSource: Boolean = true,
    val isSupported: Boolean = true,
    val isLoading: Boolean = true,
    val recentMovies: List<VodMovieInfo> = emptyList(),
    val recentShows: List<VodShowInfo> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Drives the VOD tab's landing screen - a fixed Movies/TV Shows hero row plus "Recently Added"
 * rows for each, mirroring Jellyfin/Emby's own Home screen shape. Kept separate from
 * VodLibraryViewModel (the category-drill-in screen reached by tapping a hero tile) rather than
 * merged into one class - the two are different nav-graph composable() nodes so hiltViewModel()
 * already gives each its own instance regardless of class sharing, and their state barely
 * overlaps (this one never touches categories/grid columns, that one never touches "recently
 * added"). Same split Jellyfin already uses (JellyfinHomeViewModel vs JellyfinLibraryViewModel).
 */
@HiltViewModel
class VodHomeViewModel @Inject constructor(
    private val vodRepository: IptvVodRepository,
    private val configRepository: IptvSourceConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VodHomeUiState())
    val uiState: StateFlow<VodHomeUiState> = _uiState

    init {
        // Observed continuously (not a one-shot check) so saving/editing a playlist in Settings
        // is reflected immediately on return to this tab, without an app restart.
        viewModelScope.launch {
            configRepository.configFlow.collect { config ->
                val hasSource = config != null
                val isSupported = config is IptvSourceConfig.Xtream
                _uiState.update { it.copy(hasSource = hasSource, isSupported = isSupported) }
                if (isSupported) loadRecent()
            }
        }
        // "Update Playlist" in Settings invalidates IptvVodRepository's per-category caches -
        // reload so the rows reflect whatever's actually current.
        viewModelScope.launch {
            configRepository.refreshEvents.collect { loadRecent() }
        }
    }

    private fun loadRecent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                coroutineScope {
                    val movies = async { vodRepository.getRecentMovies() }
                    val shows = async { vodRepository.getRecentShows() }
                    movies.await() to shows.await()
                }
            }
                .onSuccess { (movies, shows) ->
                    _uiState.update { it.copy(recentMovies = movies, recentShows = shows, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load VOD content") }
                }
        }
    }
}
