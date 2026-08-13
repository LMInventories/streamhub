package com.android.streamhub.feature.iptv.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.ui.FullscreenOverlayState
import com.android.streamhub.core.player.PlayerController
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvAppSettingsRepository
import com.android.streamhub.feature.iptv.data.IptvBrowseRepository
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import com.android.streamhub.feature.iptv.data.IptvSourceConfigRepository
import com.android.streamhub.feature.iptv.data.PreviewPlayerSize
import com.android.streamhub.feature.iptv.data.epg.EpgGridRepository
import com.android.streamhub.feature.iptv.data.epg.EpgRefreshResult
import com.android.streamhub.feature.iptv.data.favorites.IptvFavoritesRepository
import com.android.streamhub.feature.iptv.data.recent.RecentChannelsRepository
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledEventsRepository
import com.android.streamhub.feature.iptv.livetv.cast.LiveTvCastController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Provider

private const val GRID_DAYS = 7L
// Matches the real ceiling, not just a UX choice - Android's MediaCodec framework has been
// observed failing to allocate a 5th concurrent hardware video decoder on many devices
// regardless of app design, and every established multiview IPTV app (TiviMate, IPTV Smarters)
// caps at 4 for the same reason.
const val MAX_MULTIVIEW_TILES = 4

/** One live tile in the multiview grid - its own independent PlayerController/ExoPlayer instance, same as miniPlayerController but one per staged channel instead of one overall. */
data class MultiviewTile(val channel: IptvChannelInfo, val controller: PlayerController)

/** Which strip the multiview "Add Channel" picker is currently browsing - mirrors the main Live TV screen's own category browsing, but kept as separate state (see [LiveTvViewModel.selectMultiviewPickerTab]) so opening the picker never disturbs whatever category the main screen has selected underneath it. */
sealed class MultiviewPickerTab {
    data object Recent : MultiviewPickerTab()
    data object Favorites : MultiviewPickerTab()
    data object AllChannels : MultiviewPickerTab()
    data class Category(val category: IptvCategoryInfo) : MultiviewPickerTab()
}

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
    // The mini-preview expanded in place (same PlayerController/ExoPlayer instance, just resized)
    // rather than navigating to a separate full-screen route with a second player that would
    // need to rebuffer the same stream from scratch.
    val isFullscreen: Boolean = false,
    val previewPlayerSize: PreviewPlayerSize = PreviewPlayerSize.MEDIUM,
    // A transient session, not persistent background staging - see closeMultiview()'s own
    // comment for why every tile's controller is released rather than kept alive once closed.
    val multiviewTiles: List<MultiviewTile> = emptyList(),
    val isMultiviewActive: Boolean = false,
    val multiviewAudioFocusChannelId: String? = null,
    // Backs the multiview "Add Channel" picker's own category-tabbed browsing (see
    // MultiviewPickerTab) - independent of selectedCategory/channels above so browsing inside the
    // picker never leaves the main screen on a different category than the user actually left it on.
    val multiviewPickerTab: MultiviewPickerTab = MultiviewPickerTab.Recent,
    val multiviewPickerChannels: List<IptvChannelInfo> = emptyList(),
    val isLoadingMultiviewPickerChannels: Boolean = false,
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
    private val scheduledEventsRepository: ScheduledEventsRepository,
    private val recentChannelsRepository: RecentChannelsRepository,
    private val fullscreenOverlayState: FullscreenOverlayState,
    private val castController: LiveTvCastController,
    private val appSettingsRepository: IptvAppSettingsRepository,
    // A Provider, not a direct injection - PlayerController is unscoped, so calling .get() once
    // per staged multiview channel hands out a fresh independent ExoPlayer instance each time,
    // the same way Hilt already hands miniPlayerController its own separate instance below.
    private val multiviewControllerProvider: Provider<PlayerController>,
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

    val isCastAvailable: Boolean = castController.isAvailable
    val isCasting: StateFlow<Boolean> = castController.isCasting

    // Drives the fullscreen overlay's channel-switcher strip - reactive, so a channel added by
    // watching it just now shows up there immediately, not just next time the app opens.
    val recentChannels: StateFlow<List<IptvChannelInfo>> =
        recentChannelsRepository.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                if (hasSource) {
                    loadCategories()
                    prefetchEpgGrid()
                }
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
        // A session connecting mid-browse (user picks a device from the Cast button while already
        // watching something) should immediately start casting whatever's currently focused,
        // rather than requiring a channel switch first to trigger it.
        viewModelScope.launch {
            castController.isCasting.collect { casting -> if (casting) castCurrentChannel() }
        }
        viewModelScope.launch {
            appSettingsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(previewPlayerSize = settings.previewPlayerSize) }
            }
        }
        // "Resume last channel" itself is handled by resumeMiniPlayer(), called by the screen on
        // every entry (including this first one) rather than only here - see its own comment.
    }

    private fun castCurrentChannel() {
        val channel = _uiState.value.focusedChannel ?: return
        castController.loadStream(streamUrl = channel.streamUrl, title = channel.name, subtitle = _uiState.value.nowProgram?.title)
    }

    /**
     * Kicked off proactively as soon as a source exists, in parallel with loadCategories() - a
     * bulk XMLTV guide fetch/parse (EpgGridRepository.ensureFresh()) is the slow part of picking a
     * category for the first time each day, so this gives it a head start rather than only
     * starting once selectCategory() -> loadEpgGrid() gets around to it. Fire-and-forget and no-op
     * on failure: EpgGridRepository's own refreshMutex means this and any later
     * category-triggered ensureFresh() call share a single in-flight fetch rather than racing, and
     * loadEpgGrid() already surfaces a failure to the user once a category's actually selected.
     */
    private fun prefetchEpgGrid() {
        viewModelScope.launch { runCatching { epgGridRepository.ensureFresh() } }
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
                    loadEpgGrid(channels)
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
                    loadEpgGrid(channels)
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
                    loadEpgGrid(channels, forceRefresh = true)
                }
                .onFailure { e -> _uiState.update { it.copy(isLoadingChannels = false, errorMessage = e.message ?: "Failed to refresh channels") } }
        }
    }

    /**
     * Called when a channel is focused (TV D-pad) or tapped (phone) - drives the mini-player
     * preview. Selecting the channel that's already focused/previewing (a "double press" from the
     * user's perspective - pick it once to preview, pick the same one again to commit) expands
     * straight to fullscreen instead of no-opping, the same gesture the mini-player itself already
     * supports by tapping it directly.
     */
    fun focusChannel(channel: IptvChannelInfo) {
        if (channel.id == _uiState.value.focusedChannel?.id) {
            enterFullscreen()
            return
        }
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
        // Casting follows whatever's focused in the app, same as the local mini-player does -
        // no-ops internally if nothing's actually connected.
        if (isCasting.value) castCurrentChannel()

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

    fun scheduleRecording(channel: IptvChannelInfo, program: EpgProgram, startAdjustMinutes: Int, endAdjustMinutes: Int) {
        viewModelScope.launch { scheduledEventsRepository.addRecording(channel, program, startAdjustMinutes, endAdjustMinutes) }
    }

    fun scheduleReminder(channel: IptvChannelInfo, program: EpgProgram, leadMinutes: Int) {
        viewModelScope.launch { scheduledEventsRepository.addReminder(channel, program, leadMinutes) }
    }

    private fun loadEpgGrid(channels: List<IptvChannelInfo>, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpgGrid = true) }
            // ensureFresh only calls this while an actual download is in flight - if the cache
            // is already fresh, it never fires, so no progress bar flashes for a cache hit.
            val result = runCatching {
                epgGridRepository.ensureFresh(forceRefresh = forceRefresh) { progress ->
                    _uiState.update { it.copy(epgGridLoadProgress = progress) }
                }
            }.getOrElse { e -> EpgRefreshResult.Failed(e.message ?: "Unknown error", hasCachedData = false) }
            _uiState.update { it.copy(epgGridLoadProgress = null) }

            // Every failure mode gets surfaced (rather than just quietly leaving the grid empty)
            // so a real report of "the grid never shows anything" is actually diagnosable next
            // time - this used to swallow every failure silently.
            val hasEpg = when (result) {
                is EpgRefreshResult.UpToDate, is EpgRefreshResult.Fetched -> true
                is EpgRefreshResult.Failed -> result.hasCachedData
                EpgRefreshResult.NotConfigured -> false
            }
            // Unconditional, not ?: it.errorMessage - loadEpgGrid only ever runs after channels
            // already loaded successfully, so there's no other error here it could be clobbering,
            // and a later successful fetch needs to actually clear a previously-shown EPG error.
            _uiState.update { it.copy(errorMessage = (result as? EpgRefreshResult.Failed)?.let { f -> "EPG guide: ${f.reason}" }) }

            if (hasEpg) {
                val windowStart = Instant.now().truncatedTo(ChronoUnit.HOURS)
                val windowEnd = windowStart.plus(GRID_DAYS, ChronoUnit.DAYS)
                val grid = runCatching { epgGridRepository.getGrid(channels, windowStart, windowEnd) }.getOrDefault(emptyMap())
                _uiState.update {
                    it.copy(programmesByChannel = grid, gridWindowStart = windowStart, gridWindowEnd = windowEnd)
                }
            }
            _uiState.update { it.copy(isLoadingEpgGrid = false) }
        }
    }

    fun toggleMiniPlayerMute() = miniPlayerController.toggleMuted()

    fun toggleMiniPlayerPlayback() {
        if (miniPlayerUiState.value.isPlaying) miniPlayerController.pause() else miniPlayerController.play()
    }

    /**
     * Expands the mini-preview to fill the screen, in place - same PlayerController/ExoPlayer
     * instance, just resized, so nothing needs to rebuffer. Auto-unmutes since the preview no
     * longer has its own mute button to reach first (that lives in the fullscreen overlay now).
     * Also signals FullscreenOverlayState so the app's own bottom nav bar/tab row actually gets
     * out of the way - PhoneScaffold/TvScaffold's normal visibility is route-based and has no way
     * to know about this screen's internal state otherwise.
     */
    fun enterFullscreen() {
        val channel = _uiState.value.focusedChannel ?: return
        _uiState.update { it.copy(isFullscreen = true) }
        fullscreenOverlayState.setActive(true)
        miniPlayerController.setMuted(false)
        // Recorded here, not in focusChannel() - focusChannel() fires for every row tapped while
        // just browsing/scrolling a category, which would pollute "recently viewed" with channels
        // never actually watched. Entering fullscreen is a much closer match for "viewed".
        viewModelScope.launch { recentChannelsRepository.recordViewed(channel) }
    }

    fun exitFullscreen() {
        _uiState.update { it.copy(isFullscreen = false) }
        fullscreenOverlayState.setActive(false)
    }

    /** Switches to a different channel from the fullscreen overlay's recently-viewed strip - same in-place player, and re-records viewed so recency ordering reflects what's actually being watched now. */
    fun switchFullscreenChannel(channel: IptvChannelInfo) {
        focusChannel(channel)
        viewModelScope.launch { recentChannelsRepository.recordViewed(channel) }
    }

    /**
     * Called when the Live TV screen re-enters composition (including the first time it's ever
     * composed) - resumes whatever channel was already focused, or falls back to the most
     * recently-viewed one (when the "resume last channel" setting is on) if nothing is. Checking
     * on every entry rather than only once at ViewModel creation (which is where this used to
     * live) makes "resume last channel" actually mean what it says - reported as failing whenever
     * this screen was left and come back to, which a one-shot check can't recover from if this
     * ViewModel instance's state didn't survive that round trip for any reason, whereas re-deriving
     * it here does regardless of why.
     */
    fun resumeMiniPlayer() {
        if (_uiState.value.focusedChannel != null) {
            miniPlayerController.play()
            return
        }
        viewModelScope.launch {
            if (appSettingsRepository.settingsFlow.first().resumeLastChannel) {
                val channel = recentChannelsRepository.observeRecent().first().firstOrNull() ?: return@launch
                focusChannel(channel)
                // Also select the resumed channel's own category so its EPG grid shows
                // immediately, instead of leaving the plain category list on screen until the
                // user manually taps in - focusChannel() above already set focusedChannel, so
                // selectCategory()'s own "only auto-preview the first channel" guard leaves this
                // one alone rather than overwriting it.
                runCatching { browseRepository.findCategoryForChannel(channel.id) }
                    .getOrNull()
                    ?.let(::selectCategory)
            }
        }
    }

    /** Called when the Live TV screen leaves composition for a different section of the app - no point decoding/buffering a channel the user can no longer see. */
    fun pauseMiniPlayer() {
        miniPlayerController.pause()
    }

    /**
     * Entry point from the fullscreen player's own "Multiview" button - exits the single-channel
     * fullscreen view and starts a session with whatever was playing there plus the newly picked
     * channel, opening straight into the grid rather than requiring a separate "now open it"
     * step.
     */
    fun startMultiviewFromFullscreen(secondChannel: IptvChannelInfo) {
        val current = _uiState.value.focusedChannel ?: return
        exitFullscreen()
        addToMultiview(current)
        addToMultiview(secondChannel)
    }

    /** No-op past MAX_MULTIVIEW_TILES or if the channel's already staged. Auto-opens the grid the moment a second channel is added to the session. */
    fun addToMultiview(channel: IptvChannelInfo) {
        val current = _uiState.value.multiviewTiles
        if (current.size >= MAX_MULTIVIEW_TILES || current.any { it.channel.id == channel.id }) return

        val controller = multiviewControllerProvider.get()
        controller.prepare(
            PlaybackItem(
                id = channel.id,
                sourceType = SourceType.IPTV,
                title = channel.name,
                posterUrl = channel.logoUrl,
                streamUri = channel.streamUrl,
                isLive = true,
            ),
        )
        // Muted until explicitly given audio focus - see setMultiviewAudioFocus. The very first
        // tile staged is a reasonable default focus, so it's not silent the first time the grid
        // actually opens.
        val isFirstTile = current.isEmpty()
        controller.setMuted(!isFirstTile)
        val updatedTiles = current + MultiviewTile(channel, controller)
        _uiState.update {
            it.copy(
                multiviewTiles = updatedTiles,
                multiviewAudioFocusChannelId = if (isFirstTile) channel.id else it.multiviewAudioFocusChannelId,
            )
        }
        if (updatedTiles.size >= 2) {
            _uiState.update { it.copy(isMultiviewActive = true) }
            fullscreenOverlayState.setActive(true)
        }
    }

    /** Multiview doesn't mean anything with fewer than 2 streams - dropping to 1 via "Dismiss Focused" tears the whole session down rather than leaving one tile alone with no way back to it. */
    fun removeFromMultiview(channelId: String) {
        val tile = _uiState.value.multiviewTiles.firstOrNull { it.channel.id == channelId } ?: return
        val remaining = _uiState.value.multiviewTiles.filterNot { it.channel.id == channelId }
        if (remaining.size < 2) {
            closeMultiview()
            return
        }
        val wasFocused = _uiState.value.multiviewAudioFocusChannelId == channelId
        tile.controller.release()
        _uiState.update { it.copy(multiviewTiles = remaining) }
        if (wasFocused) setMultiviewAudioFocus(remaining.first().channel.id)
    }

    /**
     * A transient session, not persistent background staging - closing (the grid's own back
     * arrow, or "Dismiss Focused" dropping below 2 streams) releases every tile's controller
     * rather than leaving them playing muted with no way back to them. Starting multiview again
     * re-buffers from scratch, which is the simpler, more predictable trade-off.
     */
    fun closeMultiview() {
        _uiState.value.multiviewTiles.forEach { it.controller.release() }
        _uiState.update { it.copy(multiviewTiles = emptyList(), isMultiviewActive = false, multiviewAudioFocusChannelId = null) }
        fullscreenOverlayState.setActive(false)
    }

    fun setMultiviewAudioFocus(channelId: String) {
        _uiState.value.multiviewTiles.forEach { tile -> tile.controller.setMuted(tile.channel.id != channelId) }
        _uiState.update { it.copy(multiviewAudioFocusChannelId = channelId) }
    }

    /** Back to the default Recent tab with no stale channel list - called every time the "Add Channel"/"Multiview" picker is opened, so it never reopens on whatever tab was last browsed in a previous session. */
    fun resetMultiviewPicker() {
        _uiState.update { it.copy(multiviewPickerTab = MultiviewPickerTab.Recent, multiviewPickerChannels = emptyList()) }
    }

    /** Recent needs no fetch (already a live StateFlow the picker UI reads directly); the other three each do their own one-shot load into multiviewPickerChannels. */
    fun selectMultiviewPickerTab(tab: MultiviewPickerTab) {
        _uiState.update {
            it.copy(multiviewPickerTab = tab, multiviewPickerChannels = emptyList(), isLoadingMultiviewPickerChannels = tab != MultiviewPickerTab.Recent)
        }
        when (tab) {
            MultiviewPickerTab.Recent -> Unit
            MultiviewPickerTab.Favorites -> viewModelScope.launch {
                val channels = favoritesRepository.observeFavorites().first()
                _uiState.update { it.copy(multiviewPickerChannels = channels, isLoadingMultiviewPickerChannels = false) }
            }
            MultiviewPickerTab.AllChannels -> viewModelScope.launch {
                runCatching { browseRepository.getAllChannels() }
                    .onSuccess { channels -> _uiState.update { it.copy(multiviewPickerChannels = channels, isLoadingMultiviewPickerChannels = false) } }
                    .onFailure { _uiState.update { it.copy(isLoadingMultiviewPickerChannels = false) } }
            }
            is MultiviewPickerTab.Category -> viewModelScope.launch {
                runCatching { browseRepository.getChannels(tab.category.id) }
                    .onSuccess { channels -> _uiState.update { it.copy(multiviewPickerChannels = channels, isLoadingMultiviewPickerChannels = false) } }
                    .onFailure { _uiState.update { it.copy(isLoadingMultiviewPickerChannels = false) } }
            }
        }
    }

    override fun onCleared() {
        // Safety net - if this ViewModel is torn down while still fullscreen (shouldn't normally
        // happen since the screen composable's onDispose already calls exitFullscreen(), but a
        // process-level teardown could race with that), don't leave the bottom bar/tab row
        // permanently hidden for whatever screen the user lands on next.
        fullscreenOverlayState.setActive(false)
        miniPlayerController.release()
        _uiState.value.multiviewTiles.forEach { it.controller.release() }
    }
}
