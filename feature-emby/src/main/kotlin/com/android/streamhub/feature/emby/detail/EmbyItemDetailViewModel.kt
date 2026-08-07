package com.android.streamhub.feature.emby.detail

import androidx.lifecycle.SavedStateHandle
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

data class EmbyItemDetailUiState(
    val isLoading: Boolean = true,
    val item: EmbyItemInfo? = null,
    val errorMessage: String? = null,
)

/**
 * Loads a single Emby item (movie or episode) for the detail screen - deliberately thin next to
 * JellyfinItemDetailViewModel: no subtitle/audio/version picker hydration (no picker UI this
 * pass, playback just rides server/ExoPlayer default track selection), no DownloadTracker wiring
 * (no download button this pass), no favorite/watched toggles (EmbyItemInfo doesn't even carry
 * those fields yet). All per the approved plan's locked-in MVP scope for this module.
 */
@HiltViewModel
class EmbyItemDetailViewModel @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(EmbyItemDetailUiState())
    val uiState: StateFlow<EmbyItemDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val item = runCatching { browseRepository.getItem(itemId) }.getOrNull()
            _uiState.update { it.copy(isLoading = false, item = item, errorMessage = if (item == null) "Item not found" else null) }
        }
    }
}
