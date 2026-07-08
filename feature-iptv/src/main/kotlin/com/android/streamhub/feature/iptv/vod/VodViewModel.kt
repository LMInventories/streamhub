package com.android.streamhub.feature.iptv.vod

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

data class VodUiState(
    // Defaults true so the first frame shows the normal loading spinner rather than a flash of
    // the "add playlist" prompt before the DataStore read resolves.
    val hasSource: Boolean = true,
    // Distinct from hasSource: a source can be configured (Live TV works fine) but be M3U,
    // which has no standardized way to separate VOD from live channels.
    val isSupported: Boolean = true,
    val mode: VodMode = VodMode.MOVIES,
    val categories: List<VodCategoryInfo> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val selectedCategory: VodCategoryInfo? = null,
    val movies: List<VodMovieInfo> = emptyList(),
    val shows: List<VodShowInfo> = emptyList(),
    val isLoadingContent: Boolean = false,
    val gridColumns: Int = 4,
    val errorMessage: String? = null,
)

val VOD_GRID_COLUMN_OPTIONS = listOf(3, 4, 5, 6)

@HiltViewModel
class VodViewModel @Inject constructor(
    private val vodRepository: IptvVodRepository,
    private val configRepository: IptvSourceConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VodUiState())
    val uiState: StateFlow<VodUiState> = _uiState

    init {
        // Observed continuously (not a one-shot check) so saving/editing a playlist in Settings
        // is reflected immediately on return to this tab, without an app restart.
        viewModelScope.launch {
            configRepository.configFlow.collect { config ->
                val hasSource = config != null
                val isSupported = config is IptvSourceConfig.Xtream
                _uiState.update { it.copy(hasSource = hasSource, isSupported = isSupported) }
                if (isSupported) loadCategories()
            }
        }
        // "Update Playlist" in Settings - IptvVodRepository has no cache of its own (every call
        // already hits the network fresh), so refreshing just means re-running whatever's
        // currently visible.
        viewModelScope.launch {
            configRepository.refreshEvents.collect {
                loadCategories()
                _uiState.value.selectedCategory?.let(::selectCategory)
            }
        }
    }

    fun setMode(mode: VodMode) {
        if (mode == _uiState.value.mode) return
        _uiState.update {
            it.copy(mode = mode, selectedCategory = null, movies = emptyList(), shows = emptyList())
        }
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true, errorMessage = null) }
            runCatching {
                when (_uiState.value.mode) {
                    VodMode.MOVIES -> vodRepository.getCategories()
                    VodMode.SHOWS -> vodRepository.getSeriesCategories()
                }
            }
                .onSuccess { categories -> _uiState.update { it.copy(categories = categories, isLoadingCategories = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingCategories = false, errorMessage = e.message ?: "Failed to load categories") } }
        }
    }

    fun selectCategory(category: VodCategoryInfo) {
        _uiState.update { it.copy(selectedCategory = category, movies = emptyList(), shows = emptyList(), isLoadingContent = true) }
        viewModelScope.launch {
            when (_uiState.value.mode) {
                VodMode.MOVIES -> runCatching { vodRepository.getMovies(category.id) }
                    .onSuccess { movies -> _uiState.update { it.copy(movies = movies, isLoadingContent = false) } }
                    .onFailure { e -> _uiState.update { it.copy(isLoadingContent = false, errorMessage = e.message ?: "Failed to load movies") } }

                VodMode.SHOWS -> runCatching { vodRepository.getShows(category.id) }
                    .onSuccess { shows -> _uiState.update { it.copy(shows = shows, isLoadingContent = false) } }
                    .onFailure { e -> _uiState.update { it.copy(isLoadingContent = false, errorMessage = e.message ?: "Failed to load shows") } }
            }
        }
    }

    fun clearCategorySelection() {
        _uiState.update { it.copy(selectedCategory = null, movies = emptyList(), shows = emptyList()) }
    }

    fun setGridColumns(count: Int) {
        if (count !in VOD_GRID_COLUMN_OPTIONS) return
        _uiState.update { it.copy(gridColumns = count) }
    }
}
