package com.android.streamhub.feature.jellyfin.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadTracker
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val downloadTracker: DownloadTracker,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(JellyfinItemDetailUiState())
    val uiState: StateFlow<JellyfinItemDetailUiState> = _uiState

    val downloadInfo: StateFlow<DownloadInfo?> = downloadTracker.downloads
        .map { downloads -> downloads.firstOrNull { it.id == itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun toggleWatched() {
        val item = _uiState.value.item ?: return
        // Same optimistic-then-reconcile shape as toggleFavorite above.
        val optimistic = !item.isPlayed
        _uiState.update { it.copy(item = it.item?.copy(isPlayed = optimistic)) }
        viewModelScope.launch {
            val confirmed = browseRepository.toggleWatched(item.id, item.isPlayed)
            if (confirmed != null) {
                _uiState.update { it.copy(item = it.item?.copy(isPlayed = confirmed)) }
            } else {
                _uiState.update { it.copy(item = it.item?.copy(isPlayed = item.isPlayed)) }
            }
        }
    }

    // JellyfinItemInfo (unlike VOD's VodDetailInfo) doesn't carry a resolved stream URL up
    // front - getStreamUrl() involves its own PlaybackInfo/transcode-negotiation round-trip, so
    // it's only ever fetched lazily, right when actually needed (playback, or here).
    fun startDownload() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            val streamUrl = runCatching { browseRepository.getStreamUrl(item.id) }.getOrNull() ?: return@launch
            downloadTracker.startDownload(itemId, SourceType.JELLYFIN, item.name, item.primaryImageUrl, streamUrl)
        }
    }

    fun pauseDownload() = downloadTracker.pauseDownload(itemId)
    fun resumeDownload() = downloadTracker.resumeDownload(itemId)
    fun removeDownload() = downloadTracker.removeDownload(itemId)
}
