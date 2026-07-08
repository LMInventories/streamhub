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
import com.android.streamhub.feature.iptv.data.IptvSourceConfigRepository
import com.android.streamhub.feature.iptv.data.epgKey
import com.android.streamhub.feature.iptv.data.epg.EpgGridRepository
import com.android.streamhub.feature.iptv.data.favorites.IptvFavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val GRID_DAYS = 7L

data class LiveTvUiState(
    // Defaults true so the first frame shows the normal loading spinner rather than a flash of
    // the "add playlist" prompt before the DataStore read (fast, but not instant) resolves.
    val hasSource: Boolean = true,
    val categories: List<IptvCategoryInfo> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val selectedCategory: IptvCategoryInfo? = null,
    val channels: List<IptvChannelInfo> = emptyList(),
    val isLoadingChannels: Boolean = false,
    val focusedChannel: IptvChannelInfo? = null,
    val nowProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    // Reactive - updates immediately everywhere (pinned category, every channel row's long-press
    // menu) as soon as a favourite is added/removed anywhere, not just within the list it was
    // toggled from.
    val favoriteChannelIds: Set<String> = emptySet(),
    // Landscape-only 7-day grid data - loaded alongside the channel list but only rendered by
    // the landscape layout (portrait/TV-card layouts ignore it), per "EPG should only appear
    // when landscape, not as a separate screen".
    // True for the whole fetch-and-populate sequence, not just the tracked-progress download -
    // epgGridLoadProgress alone leaves a gap (Room read / cache hit / a fetch too fast to catch
    // a progress callback) where the grid would otherwise render as an empty timeline with no
    // explanation. This flag is what actually gates showing a loading indicator; progress is
    // just extra detail shown when we happen to have it.
    val isLoadingEpgGrid: Boolean = false,
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
    private val configRepository: IptvSourceConfigRepository,
    private val favoritesRepository: IptvFavoritesRepository,
    val miniPlayerController: PlayerController,
) : ViewModel() {

    companion object {
        // Not a real provider category id - a sentinel selectCategory() branches on to show the
        // aggregated favourites list instead of fetching a category's channels.
        const val FAVORITES_CATEGORY_ID = "__favorites__"
        val FAVORITES_CATEGORY = IptvCategoryInfo(id = FAVORITES_CATEGORY_ID, name = "Favourites")
    }

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState

    val miniPlayerUiState: StateFlow<PlayerUiState> = miniPlayerController.uiState

    // Only non-null while the Favourites category is selected - a live Flow collector (not a
    // one-shot fetch like every other category) so removing a favourite while looking at this
    // list updates it immediately, matching every other favourite-driven UI update.
    private var favoritesCollectJob: Job? = null

    init {
        miniPlayerController.setMuted(true)
        viewModelScope.launch {
            favoritesRepository.observeFavoriteIds().collect { ids ->
                _uiState.update { it.copy(favoriteChannelIds = ids) }
            }
        }
        // Observed continuously (not a one-shot check) so saving a playlist in Settings and
        // navigating back to Live TV - without restarting the app - immediately swaps the "add
        // playlist" prompt for the real browse UI, and editing an existing source re-fetches.
        viewModelScope.launch {
            configRepository.configFlow.collect { config ->
                val hasSource = config != null
                _uiState.update { it.copy(hasSource = hasSource) }
                if (hasSource) loadCategories()
            }
        }
        // "Update Playlist" in Settings - re-saving the same config doesn't change configFlow's
        // emitted value, so this is a separate explicit signal to drop caches and refetch.
        viewModelScope.launch {
            configRepository.refreshEvents.collect {
                browseRepository.invalidateCache()
                loadCategories()
                refreshCurrentSelection()
            }
        }
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
        favoritesCollectJob?.cancel()
        favoritesCollectJob = null

        _uiState.update { it.copy(selectedCategory = category, channels = emptyList(), isLoadingChannels = true) }

        if (category.id == FAVORITES_CATEGORY_ID) {
            favoritesCollectJob = viewModelScope.launch {
                favoritesRepository.observeFavorites().collect { channels ->
                    _uiState.update { it.copy(channels = channels, isLoadingChannels = false) }
                    if (_uiState.value.focusedChannel == null) {
                        channels.firstOrNull()?.let(::focusChannel)
                    }
                    loadEpgGrid(channels.map { it.epgKey })
                }
            }
            return
        }

        viewModelScope.launch {
            runCatching { browseRepository.getChannels(category.id) }
                .onSuccess { channels ->
                    _uiState.update { it.copy(channels = channels, isLoadingChannels = false) }
                    // Only auto-preview a channel the first time - once something's already
                    // playing, browsing into a different category shouldn't interrupt it.
                    if (_uiState.value.focusedChannel == null) {
                        channels.firstOrNull()?.let(::focusChannel)
                    }
                    loadEpgGrid(channels.map { it.epgKey })
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingChannels = false, errorMessage = e.message ?: "Failed to load channels") } }
        }
    }

    /** Deliberately leaves focusedChannel/the mini-player alone - going back to the category list shouldn't interrupt whatever's previewing. */
    fun clearCategorySelection() {
        favoritesCollectJob?.cancel()
        favoritesCollectJob = null
        _uiState.update { it.copy(selectedCategory = null, channels = emptyList()) }
    }

    /** Re-fetches the currently selected category's channels + force-refreshes its EPG - used by "Update Playlist". Leaves focusedChannel/selectedCategory alone. */
    private fun refreshCurrentSelection() {
        val category = _uiState.value.selectedCategory ?: return
        if (category.id == FAVORITES_CATEGORY_ID) return // already reactive, nothing to force-refresh
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChannels = true) }
            runCatching { browseRepository.getChannels(category.id) }
                .onSuccess { channels ->
                    _uiState.update { it.copy(channels = channels, isLoadingChannels = false) }
                    loadEpgGrid(channels.map { it.id }, forceRefresh = true)
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingChannels = false, errorMessage = e.message ?: "Failed to refresh channels") } }
        }
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

    fun toggleFavorite(channel: IptvChannelInfo) {
        viewModelScope.launch {
            if (channel.id in _uiState.value.favoriteChannelIds) {
                favoritesRepository.removeFavorite(channel.id)
            } else {
                favoritesRepository.addFavorite(channel)
            }
        }
    }

    private fun loadEpgGrid(channelIds: List<String>, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpgGrid = true) }
            // ensureFresh only calls this while an actual download is in flight - if the cache
            // is already fresh, it never fires, so no progress bar flashes for a cache hit.
            val hasEpg = runCatching {
                epgGridRepository.ensureFresh(forceRefresh = forceRefresh) { progress ->
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
            _uiState.update { it.copy(isLoadingEpgGrid = false) }
        }
    }

    fun toggleMiniPlayerMute() = miniPlayerController.toggleMuted()

    /** Called when the Live TV screen re-enters composition (including the first time) - resumes whatever channel was already focused. */
    fun resumeMiniPlayer() {
        if (_uiState.value.focusedChannel != null) miniPlayerController.play()
    }

    /** Called when the Live TV screen leaves composition for a different section of the app (or fullscreen) - no point decoding/buffering a channel the user can no longer see. */
    fun pauseMiniPlayer() {
        miniPlayerController.pause()
    }

    override fun onCleared() {
        miniPlayerController.release()
    }
}
