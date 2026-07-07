package com.android.streamhub.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.player.ExternalPlayerLauncher
import com.android.streamhub.core.player.PlayerController
import com.android.streamhub.core.player.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
    private val mediaSources: Set<@JvmSuppressWildcards MediaSource>,
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
                source.resolvePlayback(itemId)
            }.onSuccess { item ->
                resolvedItem = item
                playerController.prepare(item)
            }.onFailure { throwable ->
                playerController.reportError(throwable.message ?: "Failed to resolve playback item")
            }
        }
    }

    fun togglePlayPause() {
        if (uiState.value.isPlaying) playerController.pause() else playerController.play()
    }

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun selectAudioTrack(trackId: String) = playerController.selectAudioTrack(trackId)

    fun selectSubtitleTrack(trackId: String) = playerController.selectTextTrack(trackId)

    fun clearSubtitles() = playerController.clearTextTrack()

    /** Returns true if handoff to an external player actually launched. */
    fun openExternally(context: Context): Boolean {
        val item = resolvedItem ?: return false
        return externalPlayerLauncher.launch(context, item)
    }

    override fun onCleared() {
        playerController.release()
    }
}
