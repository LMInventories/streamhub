package com.android.streamhub.feature.jellyfin.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType
import com.android.streamhub.feature.jellyfin.data.JellyfinSortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 60

data class JellyfinLibraryUiState(
    val libraryName: String = "",
    val items: List<JellyfinItemInfo> = emptyList(),
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val sortOption: JellyfinSortOption = JellyfinSortOption.NAME_ASC,
    val favoritesOnly: Boolean = false,
    val unwatchedOnly: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class JellyfinLibraryViewModel @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val libraryId: String = checkNotNull(savedStateHandle["libraryId"])
    private val itemType: JellyfinItemType = JellyfinItemType.valueOf(checkNotNull(savedStateHandle["itemType"]))

    private val _uiState = MutableStateFlow(JellyfinLibraryUiState())
    val uiState: StateFlow<JellyfinLibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            // The library's own name isn't passed via the route (see Route.jellyfinLibraryRoute) -
            // looked up here instead, matching how ItemDetailScreen fetches its own title rather
            // than carrying it through nav args.
            val name = browseRepository.getLibraries().firstOrNull { it.id == libraryId }?.name.orEmpty()
            _uiState.update { it.copy(libraryName = name) }
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
                    favoritesOnly = state.favoritesOnly,
                    unwatchedOnly = state.unwatchedOnly,
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

    fun setSortOption(option: JellyfinSortOption) = reloadWith { it.copy(sortOption = option) }
    fun setFavoritesOnly(enabled: Boolean) = reloadWith { it.copy(favoritesOnly = enabled) }
    fun setUnwatchedOnly(enabled: Boolean) = reloadWith { it.copy(unwatchedOnly = enabled) }

    /** Changing sort/filter invalidates the whole loaded page set (the server, not this list, owns ordering/filtering) - reset and reload from the start rather than trying to reconcile the existing pages. */
    private fun reloadWith(transform: (JellyfinLibraryUiState) -> JellyfinLibraryUiState) {
        _uiState.update { transform(it).copy(items = emptyList(), canLoadMore = true) }
        loadMore()
    }
}
