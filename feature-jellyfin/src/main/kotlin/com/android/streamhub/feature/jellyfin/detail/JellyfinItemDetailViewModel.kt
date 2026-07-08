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
}
