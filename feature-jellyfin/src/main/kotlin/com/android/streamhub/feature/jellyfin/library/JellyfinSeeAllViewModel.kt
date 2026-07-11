package com.android.streamhub.feature.jellyfin.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSectionKeys
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Both rows are inherently bounded/curated lists (Jellyfin's own Resume/NextUp endpoints, not a
// full library browse) rather than a large catalog someone would page through - a single larger
// fetch covers "See All" without needing real startIndex-based pagination the way Favourites/
// Library do.
private const val SEE_ALL_LIMIT = 100

data class JellyfinSeeAllUiState(
    val title: String = "",
    val items: List<JellyfinItemInfo> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Backs the "See All" destination for Continue Watching and Next Up - one ViewModel rather than
 * two near-identical ones (unlike JellyfinFavoritesViewModel/JellyfinLibraryViewModel, which take
 * genuinely different parameters) since these two really are the same shape: load once, show a
 * flat grid, no filter toggle, no real pagination. [kind] picks which one via the nav arg, same
 * pattern PlayerViewModel/ItemDetailViewModel already use to branch on a route arg.
 */
@HiltViewModel
class JellyfinSeeAllViewModel @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kind: String = checkNotNull(savedStateHandle["kind"]) { "kind is required" }

    private val _uiState = MutableStateFlow(
        JellyfinSeeAllUiState(title = if (kind == JellyfinHomeSectionKeys.NEXT_UP) "Next Up" else "Continue Watching"),
    )
    val uiState: StateFlow<JellyfinSeeAllUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                if (kind == JellyfinHomeSectionKeys.NEXT_UP) {
                    browseRepository.getNextUp(limit = SEE_ALL_LIMIT)
                } else {
                    browseRepository.getResumeItems(limit = SEE_ALL_LIMIT)
                }
            }.onSuccess { items -> _uiState.update { it.copy(isLoading = false, items = items) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load") } }
        }
    }

    // JellyfinItemGrid calls this once the user scrolls near the end - a no-op here since load()
    // above already fetches the full (bounded) list in one shot, nothing further to page in.
    fun loadMore() = Unit
}
