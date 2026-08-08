package com.android.streamhub.feature.emby.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 60

data class EmbyFavoritesUiState(
    val items: List<EmbyItemInfo> = emptyList(),
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val errorMessage: String? = null,
)

/** Same shape as EmbyLibraryViewModel but backed by getFavorites() (cross-library, mixed movies/series) instead of getItems() (one library, one item type) - kept separate rather than unifying since the two calls have genuinely different parameters, not just different arguments to the same one. Mirrors JellyfinFavoritesViewModel exactly. */
@HiltViewModel
class EmbyFavoritesViewModel @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmbyFavoritesUiState())
    val uiState: StateFlow<EmbyFavoritesUiState> = _uiState

    init {
        loadMore()
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.canLoadMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val startIndex = _uiState.value.items.size
            runCatching { browseRepository.getFavorites(startIndex, PAGE_SIZE) }
                .onSuccess { newItems ->
                    _uiState.update {
                        it.copy(items = it.items + newItems, isLoading = false, canLoadMore = newItems.size == PAGE_SIZE)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load favourites") } }
        }
    }
}
