package com.android.streamhub.feature.emby.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType
import com.android.streamhub.feature.emby.data.EmbySortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 60

data class EmbyLibraryUiState(
    val libraryName: String = "",
    val items: List<EmbyItemInfo> = emptyList(),
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val sortOption: EmbySortOption = EmbySortOption.NAME_ASC,
    // Populated once via getLibraryFilterOptions (see init) - the full set of genres/years
    // available to pick from, independent of whatever's currently selected/loaded.
    val availableGenres: List<String> = emptyList(),
    val availableYears: List<Int> = emptyList(),
    val selectedGenre: String? = null,
    val selectedYear: Int? = null,
    val errorMessage: String? = null,
)

/**
 * Paginated single-library browse - sort/genre/year this pass (no favourites/unwatched filter,
 * unlike JellyfinLibraryViewModel; those remain out of scope per the plan's locked-in MVP scope).
 */
@HiltViewModel
class EmbyLibraryViewModel @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val libraryId: String = checkNotNull(savedStateHandle["libraryId"])
    private val itemType: EmbyItemType = EmbyItemType.valueOf(checkNotNull(savedStateHandle["itemType"]))

    private val _uiState = MutableStateFlow(EmbyLibraryUiState())
    val uiState: StateFlow<EmbyLibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            // The library's own name isn't passed via the route - looked up here instead, same
            // pattern as JellyfinLibraryViewModel (matching ItemDetailScreen fetching its own
            // title rather than carrying it through nav args).
            val name = browseRepository.getLibraries().firstOrNull { it.id == libraryId }?.name.orEmpty()
            _uiState.update { it.copy(libraryName = name) }
        }
        viewModelScope.launch {
            val options = browseRepository.getLibraryFilterOptions(libraryId, itemType)
            _uiState.update { it.copy(availableGenres = options.genres, availableYears = options.years) }
        }
        loadMore()
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            val startIndex = state.items.size
            runCatching {
                browseRepository.getItems(
                    libraryId = libraryId,
                    itemType = itemType,
                    startIndex = startIndex,
                    limit = PAGE_SIZE,
                    sortOption = state.sortOption,
                    genre = state.selectedGenre,
                    year = state.selectedYear,
                )
            }
                .onSuccess { newItems ->
                    _uiState.update {
                        it.copy(items = it.items + newItems, isLoading = false, canLoadMore = newItems.size == PAGE_SIZE)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load library") } }
        }
    }

    fun setSortOption(option: EmbySortOption) = reloadWith { it.copy(sortOption = option) }
    // null clears the filter ("All Genres"/"All Years").
    fun setGenre(genre: String?) = reloadWith { it.copy(selectedGenre = genre) }
    fun setYear(year: Int?) = reloadWith { it.copy(selectedYear = year) }

    /** Sort/filters are server-side (the server, not this list, owns ordering/filtering) - changing any of them invalidates the whole loaded page set, so reset and reload from the start rather than trying to reconcile the existing pages. */
    private fun reloadWith(transform: (EmbyLibraryUiState) -> EmbyLibraryUiState) {
        _uiState.update { transform(it).copy(items = emptyList(), canLoadMore = true) }
        loadMore()
    }
}
