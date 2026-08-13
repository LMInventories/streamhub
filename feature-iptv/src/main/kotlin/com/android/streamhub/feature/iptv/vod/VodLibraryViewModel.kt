package com.android.streamhub.feature.iptv.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.IptvSourceConfig
import com.android.streamhub.feature.iptv.data.IptvSourceConfigRepository
import com.android.streamhub.feature.iptv.data.IptvVodRepository
import com.android.streamhub.feature.iptv.data.VodCategoryInfo
import com.android.streamhub.feature.iptv.data.VodMovieInfo
import com.android.streamhub.feature.iptv.data.VodShowInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VodMode { MOVIES, SHOWS }

val VOD_GRID_COLUMN_OPTIONS = listOf(3, 4, 5, 6)

data class VodLibraryUiState(
    val mode: VodMode,
    // Defaults true so the first frame shows the normal loading spinner rather than a flash of
    // the "add playlist" prompt before the DataStore read resolves.
    val hasSource: Boolean = true,
    // Distinct from hasSource: a source can be configured (Live TV works fine) but be M3U,
    // which has no standardized way to separate VOD from live channels.
    val isSupported: Boolean = true,
    val categories: List<VodCategoryInfo> = emptyList(),
    val isLoadingCategories: Boolean = true,
    // null = the "All" chip - every item across every category, merged.
    val selectedCategoryId: String? = null,
    val movies: List<VodMovieInfo> = emptyList(),
    val shows: List<VodShowInfo> = emptyList(),
    val isLoadingContent: Boolean = true,
    val gridColumns: Int = 4,
    val errorMessage: String? = null,
)

/**
 * Drives the VOD Library screen - one instance per mode (Movies or TV Shows), reached by tapping
 * a hero tile or "See All" on VOD Home. Mode is fixed for this screen's lifetime, read once from
 * the nav arg via SavedStateHandle (same pattern as Jellyfin/Emby's own LibraryViewModels reading
 * libraryId/itemType) - there's no in-screen mode switch, matching JellyfinLibraryScreen's own
 * lack of one (switching means going back to Home).
 *
 * Defaults to the "All" chip (selectedCategoryId = null) rather than requiring a category tap
 * first - in the normal flow this is effectively free: VOD Home already called
 * IptvVodRepository.getRecentMovies()/getRecentShows() to build its rows, which internally calls
 * getAllMovies()/getAllShows() and warms every category's cache as a side effect, so by the time
 * a hero tile is tapped this screen's own "All" fetch just replays that cache.
 */
@HiltViewModel
class VodLibraryViewModel @Inject constructor(
    private val vodRepository: IptvVodRepository,
    private val configRepository: IptvSourceConfigRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mode: VodMode = VodMode.valueOf(checkNotNull(savedStateHandle["mode"]) { "mode is required" })

    private val _uiState = MutableStateFlow(VodLibraryUiState(mode = mode))
    val uiState: StateFlow<VodLibraryUiState> = _uiState

    init {
        // Observed continuously (not a one-shot check) so saving/editing a playlist in Settings
        // is reflected immediately on return to this tab, without an app restart.
        viewModelScope.launch {
            configRepository.configFlow.collect { config ->
                val hasSource = config != null
                val isSupported = config is IptvSourceConfig.Xtream
                _uiState.update { it.copy(hasSource = hasSource, isSupported = isSupported) }
                if (isSupported) {
                    loadCategories()
                    loadContent(_uiState.value.selectedCategoryId)
                }
            }
        }
        // "Update Playlist" in Settings invalidates IptvVodRepository's per-category caches -
        // reload categories and whatever's currently selected.
        viewModelScope.launch {
            configRepository.refreshEvents.collect {
                loadCategories()
                loadContent(_uiState.value.selectedCategoryId)
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true, errorMessage = null) }
            runCatching {
                when (mode) {
                    VodMode.MOVIES -> vodRepository.getCategories()
                    VodMode.SHOWS -> vodRepository.getSeriesCategories()
                }
            }
                .onSuccess { categories -> _uiState.update { it.copy(categories = categories, isLoadingCategories = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingCategories = false, errorMessage = e.message ?: "Failed to load categories") } }
        }
    }

    /** null selects the "All" chip. */
    fun selectCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        loadContent(categoryId)
    }

    private fun loadContent(categoryId: String?) {
        _uiState.update { it.copy(movies = emptyList(), shows = emptyList(), isLoadingContent = true) }
        viewModelScope.launch {
            when (mode) {
                VodMode.MOVIES -> runCatching { if (categoryId == null) vodRepository.getAllMovies() else vodRepository.getMovies(categoryId) }
                    .onSuccess { movies -> _uiState.update { it.copy(movies = movies, isLoadingContent = false) } }
                    .onFailure { e -> _uiState.update { it.copy(isLoadingContent = false, errorMessage = e.message ?: "Failed to load movies") } }

                VodMode.SHOWS -> runCatching { if (categoryId == null) vodRepository.getAllShows() else vodRepository.getShows(categoryId) }
                    .onSuccess { shows -> _uiState.update { it.copy(shows = shows, isLoadingContent = false) } }
                    .onFailure { e -> _uiState.update { it.copy(isLoadingContent = false, errorMessage = e.message ?: "Failed to load shows") } }
            }
        }
    }

    fun setGridColumns(count: Int) {
        if (count !in VOD_GRID_COLUMN_OPTIONS) return
        _uiState.update { it.copy(gridColumns = count) }
    }
}
