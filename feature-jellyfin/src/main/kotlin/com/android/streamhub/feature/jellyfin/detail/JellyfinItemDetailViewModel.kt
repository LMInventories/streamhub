package com.android.streamhub.feature.jellyfin.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JellyfinItemDetailUiState(
    val isLoading: Boolean = true,
    val item: JellyfinItemInfo? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class JellyfinItemDetailViewModel @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(JellyfinItemDetailUiState())
    val uiState: StateFlow<JellyfinItemDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val item = runCatching { browseRepository.getItem(itemId) }.getOrNull()
            _uiState.update { it.copy(isLoading = false, item = item, errorMessage = if (item == null) "Item not found" else null) }
        }
    }

    fun toggleFavorite() {
        val item = _uiState.value.item ?: return
        // Optimistic - flips immediately so the button feels responsive, then reconciles with
        // whatever the server actually confirms (or reverts silently on failure).
        val optimistic = !item.isFavorite
        _uiState.update { it.copy(item = it.item?.copy(isFavorite = optimistic)) }
        viewModelScope.launch {
            val confirmed = browseRepository.toggleFavorite(item.id, item.isFavorite)
            if (confirmed != null) {
                _uiState.update { it.copy(item = it.item?.copy(isFavorite = confirmed)) }
            } else {
                _uiState.update { it.copy(item = it.item?.copy(isFavorite = item.isFavorite)) }
            }
        }
    }
}
