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
import com.android.streamhub.feature.iptv.data.epg.EpgGridRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val GRID_DAYS = 7L

data class LiveTvUiState(
    val categories: List<IptvCategoryInfo> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val selectedCategory: IptvCategoryInfo? = null,
    val channels: List<IptvChannelInfo> = emptyList(),
    val isLoadingChannels: Boolean = false,
    val focusedChannel: IptvChannelInfo? = null,
    val nowProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    // Landscape-only 7-day grid data - loaded alongside the channel list but only rendered by
    // the landscape layout (portrait/TV-card layouts ignore it), per "EPG should only appear
    // when landscape, not as a separate screen".
    val epgGridLoadProgress: Float? = null,
    val programmesByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val gridWindowStart: Instant = Instant.now().truncatedTo(ChronoUnit.HOURS),
    val gridWindowEnd: Instant = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(GRID_DAYS, ChronoUnit.DAYS),
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
    private val epgGridRepository: EpgGridRepository,
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
                    loadEpgGrid(channels.map { it.id })
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

    private fun loadEpgGrid(channelIds: List<String>) {
        viewModelScope.launch {
            // ensureFresh only calls this while an actual download is in flight - if the cache
            // is already fresh, it never fires, so no progress bar flashes for a cache hit.
            val hasEpg = runCatching {
                epgGridRepository.ensureFresh { progress ->
                    _uiState.update { it.copy(epgGridLoadProgress = progress) }
                }
            }.getOrDefault(false)
            _uiState.update { it.copy(epgGridLoadProgress = null) }

            if (hasEpg) {
                val windowStart = Instant.now().truncatedTo(ChronoUnit.HOURS)
                val windowEnd = windowStart.plus(GRID_DAYS, ChronoUnit.DAYS)
                val grid = runCatching { epgGridRepository.getGrid(channelIds, windowStart, windowEnd) }.getOrDefault(emptyMap())
                _uiState.update {
                    it.copy(programmesByChannel = grid, gridWindowStart = windowStart, gridWindowEnd = windowEnd)
                }
            }
        }
    }

    fun toggleMiniPlayerMute() = miniPlayerController.toggleMuted()

    override fun onCleared() {
        miniPlayerController.release()
    }
}
