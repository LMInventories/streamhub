package com.android.streamhub.feature.jellyfin.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType
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
            val startIndex = _uiState.value.items.size
            runCatching { browseRepository.getItems(libraryId, itemType, startIndex, PAGE_SIZE) }
                .onSuccess { newItems ->
                    _uiState.update {
                        it.copy(items = it.items + newItems, isLoading = false, canLoadMore = newItems.size == PAGE_SIZE)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load library") } }
        }
    }
}
