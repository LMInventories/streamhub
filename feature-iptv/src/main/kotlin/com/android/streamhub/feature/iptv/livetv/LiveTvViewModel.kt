package com.android.streamhub.feature.iptv.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.player.PlayerController
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvBrowseRepository
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveTvUiState(
    val categories: List<IptvCategoryInfo> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val selectedCategory: IptvCategoryInfo? = null,
    val channels: List<IptvChannelInfo> = emptyList(),
    val isLoadingChannels: Boolean = false,
    val focusedChannel: IptvChannelInfo? = null,
    val nowProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val errorMessage: String? = null,
)

/**
 * [miniPlayerController] is a second, independent ExoPlayer instance from the full-screen
 * player's - both are unscoped PlayerController injections, so Hilt hands out a fresh one here
 * tied to this ViewModel's lifecycle instead of reusing the player screen's.
 */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val browseRepository: IptvBrowseRepository,
    val miniPlayerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState

    val miniPlayerUiState: StateFlow<PlayerUiState> = miniPlayerController.uiState

    init {
        miniPlayerController.setMuted(true)
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true, errorMessage = null) }
            runCatching { browseRepository.getCategories() }
                .onSuccess { categories -> _uiState.update { it.copy(categories = categories, isLoadingCategories = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingCategories = false, errorMessage = e.message ?: "Failed to load categories") } }
        }
    }

    fun selectCategory(category: IptvCategoryInfo) {
        _uiState.update { it.copy(selectedCategory = category, channels = emptyList(), isLoadingChannels = true) }
        viewModelScope.launch {
            runCatching { browseRepository.getChannels(category.id) }
                .onSuccess { channels ->
                    _uiState.update { it.copy(channels = channels, isLoadingChannels = false) }
                    // Only auto-preview a channel the first time - once something's already
                    // playing, browsing into a different category shouldn't interrupt it.
                    if (_uiState.value.focusedChannel == null) {
                        channels.firstOrNull()?.let(::focusChannel)
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingChannels = false, errorMessage = e.message ?: "Failed to load channels") } }
        }
    }

    /** Deliberately leaves focusedChannel/the mini-player alone - going back to the category list shouldn't interrupt whatever's previewing. */
    fun clearCategorySelection() {
        _uiState.update { it.copy(selectedCategory = null, channels = emptyList()) }
    }

    /** Called when a channel is focused (TV D-pad) or tapped (phone) - drives the mini-player preview. */
    fun focusChannel(channel: IptvChannelInfo) {
        if (channel.id == _uiState.value.focusedChannel?.id) return
        _uiState.update { it.copy(focusedChannel = channel, nowProgram = null, nextProgram = null) }

        miniPlayerController.prepare(
            PlaybackItem(
                id = channel.id,
                sourceType = SourceType.IPTV,
                title = channel.name,
                posterUrl = channel.logoUrl,
                streamUri = channel.streamUrl,
                isLive = true,
            ),
        )

        viewModelScope.launch {
            runCatching { browseRepository.getNowNext(channel.id) }
                .onSuccess { (now, next) -> _uiState.update { it.copy(nowProgram = now, nextProgram = next) } }
            // EPG being unavailable/failing shouldn't disrupt the live preview itself.
        }
    }

    fun toggleMiniPlayerMute() = miniPlayerController.toggleMuted()

    override fun onCleared() {
        miniPlayerController.release()
    }
}
