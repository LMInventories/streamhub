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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val mediaSources: Set<@JvmSuppressWildcards MediaSource>,
    private val watchProgressRepository: WatchProgressRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = playerController.uiState
    val exoPlayer get() = playerController.exoPlayer

    private val itemId: String = checkNotNull(savedStateHandle["itemId"]) { "itemId is required" }
    private val sourceType: SourceType = SourceType.valueOf(
        checkNotNull(savedStateHandle["sourceType"]) { "sourceType is required" },
    )

    private var resolvedItem: PlaybackItem? = null

    init {
        viewModelScope.launch {
            runCatching {
                val source = mediaSources.firstOrNull { it.sourceType == sourceType }
                    ?: error("No MediaSource registered for $sourceType")
                val item = source.resolvePlayback(itemId)
                // Resume point is a cross-source concern applied once here, rather than by every
                // MediaSource implementation - resolvePlayback only needs to answer "what to
                // play", not "where the user left off".
                if (item.isLive) return@runCatching item
                val progress = watchProgressRepository.getProgress(sourceType, itemId)
                if (progress != null && !progress.isNearlyComplete) {
                    item.copy(startPositionMs = progress.positionMs)
                } else {
                    item
                }
            }.onSuccess { item ->
                resolvedItem = item
                playerController.prepare(item)
                if (!item.isLive) startProgressReporting()
            }.onFailure { throwable ->
                playerController.reportError(throwable.message ?: "Failed to resolve playback item")
            }
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
        val item = resolvedItem ?: return false
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
        val item = resolvedItem ?: return
        if (item.isLive) return
        val state = uiState.value
        if (state.durationMs <= 0) return
        viewModelScope.launch {
            watchProgressRepository.saveProgress(sourceType, itemId, state.positionMs, state.durationMs)
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
