package com.android.streamhub.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val items: List<PlaybackItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaSources: Set<@JvmSuppressWildcards MediaSource>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    /**
     * Called from a LaunchedEffect(Unit) each time Home enters composition - including when
     * returning from Settings - rather than only once from init, so saving a new IPTV source
     * is visible without an app restart (IptvMediaSource itself decides whether that means a
     * real refetch or a cache hit).
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items = mediaSources.flatMap { it.browse() }
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }
}
