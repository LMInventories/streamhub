package com.android.streamhub.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.domain.WatchProgressRepository
import com.android.streamhub.core.player.ExternalPlayerLauncher
import com.android.streamhub.core.player.PlayerController
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.download.DownloadState
import com.android.streamhub.core.player.download.DownloadTracker
import dagger.hilt.android.lifecycle.HiltViewModel
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = playerController.uiState
    val exoPlayer get() = playerController.exoPlayer

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

    private val _currentItem = MutableStateFlow<PlaybackItem?>(null)
    val currentItem: StateFlow<PlaybackItem?> = _currentItem

    /** Empty for non-live sources (MediaSource.observeRecentlyViewed() defaults to an empty Flow) - the overlay only shows this strip when currentItem.isLive anyway. */
    val recentChannels: StateFlow<List<PlaybackItem>> =
        (mediaSource?.observeRecentlyViewed() ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { loadAndPrepare(itemId, applyResumePoint = true) }
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
            val item = if (downloaded != null) {
                PlaybackItem(
                    id = downloaded.id,
                    sourceType = downloaded.sourceType,
                    title = downloaded.title,
                    posterUrl = downloaded.posterUrl,
                    streamUri = downloaded.streamUri,
                    isLive = false,
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
    }

    override fun onCleared() {
        // Best-effort only - viewModelScope is cancelled around the same time onCleared() runs,
        // so this isn't guaranteed to complete. The periodic tick (every 10s) and the
        // save-on-pause above are what actually make resume reliable; this just narrows the gap
        // a little further for the common case where the coroutine dispatcher gets a moment
        // before full cancellation.
        saveProgressNow()
        playerController.release()
    }
}
