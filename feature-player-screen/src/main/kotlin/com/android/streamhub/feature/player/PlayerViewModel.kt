package com.android.streamhub.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.domain.SubtitlePreference
import com.android.streamhub.core.common.domain.WatchProgressRepository
import com.android.streamhub.core.player.ExternalPlayerLauncher
import com.android.streamhub.core.player.PlayerController
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.download.DownloadState
import com.android.streamhub.core.player.download.DownloadTracker
import com.android.streamhub.feature.player.cast.PlayerCastController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val mediaSources: Set<@JvmSuppressWildcards MediaSource>,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadTracker: DownloadTracker,
    private val castController: PlayerCastController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = playerController.uiState
    val exoPlayer get() = playerController.exoPlayer

    // Phone-only surface (see CastButton's own call site) - exposed here regardless so
    // PlayerViewModel stays the single source of truth for this screen's state either way.
    val isCastAvailable: Boolean = castController.isAvailable

    private val itemId: String = checkNotNull(savedStateHandle["itemId"]) { "itemId is required" }
    private val sourceType: SourceType = SourceType.valueOf(
        checkNotNull(savedStateHandle["sourceType"]) { "sourceType is required" },
    )

    // Route/savedStateHandle still name the item this screen was originally navigated to - a
    // channel switch (see switchChannel) re-prepares playback in place rather than re-navigating,
    // so this can go stale after a switch. Only matters for process-death restoration, which just
    // lands back on the originally-navigated channel rather than whichever was last switched to -
    // an accepted tradeoff against the complexity of keeping the nav route itself in sync.
    private val mediaSource: MediaSource? = mediaSources.firstOrNull { it.sourceType == sourceType }

    // viewModelScope is cancelled right *before* onCleared() runs (see that function's own
    // androidx doc), so anything launched through it from inside onCleared never actually starts -
    // its Job is already dead at that point. This scope exists solely so the final save/stop
    // report below can actually complete after the screen is gone instead of silently no-oping.
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentItem = MutableStateFlow<PlaybackItem?>(null)
    val currentItem: StateFlow<PlaybackItem?> = _currentItem

    /** Empty for non-live sources (MediaSource.observeRecentlyViewed() defaults to an empty Flow) - the overlay only shows this strip when currentItem.isLive anyway. */
    val recentChannels: StateFlow<List<PlaybackItem>> =
        (mediaSource?.observeRecentlyViewed() ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { loadAndPrepare(itemId, applyResumePoint = true) }
        // A session can connect after playback has already started (the common case - the user
        // taps Cast mid-watch), not just before, so this has to be reactive rather than a one-shot
        // check at load time. Same shape as LiveTvViewModel's own "collect isCasting -> load"
        // wiring for its mini-player.
        viewModelScope.launch { castController.isCasting.collect { casting -> if (casting) castCurrentItem() } }
    }

    private fun castCurrentItem() {
        val item = _currentItem.value ?: return
        castController.loadStream(item.streamUri, item.title, item.subtitle, item.isLive)
    }

    /** Switches playback to a different live channel without leaving this screen - used by the overlay's recently-viewed strip. */
    fun switchChannel(newItemId: String) {
        if (newItemId == _currentItem.value?.id) return
        viewModelScope.launch { loadAndPrepare(newItemId, applyResumePoint = false) }
    }

    private suspend fun loadAndPrepare(id: String, applyResumePoint: Boolean) {
        runCatching {
            val downloaded = downloadTracker.downloads.value
                .firstOrNull { it.id == id && it.sourceType == sourceType && it.state == DownloadState.COMPLETED }
            // A completed download already has everything needed to play it - going through the
            // normal MediaSource.resolvePlayback() here would mean a genuinely offline device
            // (no network at all) can't play back content it already has sitting on disk, since
            // that call still needs to reach Jellyfin/Xtream just to resolve metadata/URLs.
            // resolveSubtitlePreference() is still safe to call here (network-free by contract),
            // and without it this PlaybackItem would fall back to subtitlesOff = false regardless
            // of whatever the detail page's own subtitle picker was set to for this item.
            val item = if (downloaded != null) {
                val subtitlePreference = mediaSource?.resolveSubtitlePreference(id) ?: SubtitlePreference()
                PlaybackItem(
                    id = downloaded.id,
                    sourceType = downloaded.sourceType,
                    title = downloaded.title,
                    posterUrl = downloaded.posterUrl,
                    streamUri = downloaded.streamUri,
                    isLive = false,
                    preferredSubtitleLanguage = subtitlePreference.preferredLanguage,
                    subtitlesOff = subtitlePreference.off,
                )
            } else {
                val source = mediaSource ?: error("No MediaSource registered for $sourceType")
                source.resolvePlayback(id)
            }
            // Resume point is a cross-source concern applied once here, rather than by every
            // MediaSource implementation - resolvePlayback only needs to answer "what to
            // play", not "where the user left off".
            if (item.isLive || !applyResumePoint) return@runCatching item
            val progress = watchProgressRepository.getProgress(sourceType, id)
            if (progress != null && !progress.isNearlyComplete) {
                item.copy(startPositionMs = progress.positionMs)
            } else {
                item
            }
        }.onSuccess { item ->
            _currentItem.value = item
            playerController.prepare(item)
            if (item.isLive) {
                mediaSource?.let { source -> viewModelScope.launch { source.recordViewed(item.id) } }
            } else {
                mediaSource?.let { source -> viewModelScope.launch { source.onPlaybackStarted(item.id) } }
                startProgressReporting()
            }
        }.onFailure { throwable ->
            playerController.reportError(throwable.message ?: "Failed to resolve playback item")
        }
    }

    fun togglePlayPause() {
        if (uiState.value.isPlaying) {
            playerController.pause()
            // An explicit pause is the most common "I'm stepping away" signal - save right away
            // rather than waiting for the next periodic tick.
            saveProgressNow()
        } else {
            playerController.play()
        }
    }

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun selectAudioTrack(trackId: String) = playerController.selectAudioTrack(trackId)

    fun selectSubtitleTrack(trackId: String) = playerController.selectTextTrack(trackId)

    fun clearSubtitles() = playerController.clearTextTrack()

    fun setAspectMode(mode: VideoAspectMode) = playerController.setAspectMode(mode)

    /** Returns true if handoff to an external player actually launched. */
    fun openExternally(context: Context): Boolean {
        val item = _currentItem.value ?: return false
        return externalPlayerLauncher.launch(context, item)
    }

    private fun startProgressReporting() {
        viewModelScope.launch {
            while (true) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                saveProgressNow()
            }
        }
    }

    private fun saveProgressNow() {
        val item = _currentItem.value ?: return
        if (item.isLive) return
        val state = uiState.value
        if (state.durationMs <= 0) return
        viewModelScope.launch {
            watchProgressRepository.saveProgress(sourceType, item.id, state.positionMs, state.durationMs)
        }
        // Same position, mirrored to whichever server-side source owns this item (Jellyfin/Emby)
        // so its own apps see the same progress instead of it staying siloed in the line above.
        mediaSource?.let { source ->
            viewModelScope.launch { source.onPlaybackProgress(item.id, state.positionMs, state.durationMs, !state.isPlaying) }
        }
    }

    override fun onCleared() {
        // Uses teardownScope, not viewModelScope - see that property's own doc for why. The
        // periodic tick (every 10s) and the save-on-pause in saveProgressNow() are what carry most
        // of the load for resume reliability; this is just the final flush for whatever happened
        // since the last one of those.
        _currentItem.value?.let { item ->
            if (!item.isLive) {
                val state = uiState.value
                if (state.durationMs > 0) {
                    teardownScope.launch { watchProgressRepository.saveProgress(sourceType, item.id, state.positionMs, state.durationMs) }
                }
                mediaSource?.let { source ->
                    teardownScope.launch { source.onPlaybackStopped(item.id, state.positionMs, state.durationMs) }
                }
            }
        }
        playerController.release()
    }
}
