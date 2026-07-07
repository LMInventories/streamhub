package com.android.streamhub.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.TrackOption
import kotlinx.coroutines.delay

private const val SEEK_STEP_MS = 10_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreenTv(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(6000)
            controlsVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(player = viewModel.exoPlayer, modifier = Modifier.fillMaxSize())

        if (uiState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            TvPlayerControls(
                uiState = uiState,
                onBack = onBack,
                onPlayPause = { controlsVisible = true; viewModel.togglePlayPause() },
                onSeekBack = { viewModel.seekTo((uiState.positionMs - SEEK_STEP_MS).coerceAtLeast(0L)) },
                onSeekForward = { viewModel.seekTo((uiState.positionMs + SEEK_STEP_MS).coerceAtMost(uiState.durationMs)) },
                onAudioTrackClick = { showAudioPicker = true },
                onSubtitleTrackClick = { showSubtitlePicker = true },
                onOpenExternally = { viewModel.openExternally(context) },
            )
        }
    }

    if (showAudioPicker) {
        TvTrackPickerDialog(
            title = "Audio",
            tracks = uiState.audioTracks,
            offSelected = false,
            onSelect = { viewModel.selectAudioTrack(it); showAudioPicker = false },
            onOff = null,
            onDismiss = { showAudioPicker = false },
        )
    }
    if (showSubtitlePicker) {
        TvTrackPickerDialog(
            title = "Subtitles",
            tracks = uiState.subtitleTracks,
            offSelected = uiState.subtitlesOff,
            onSelect = { viewModel.selectSubtitleTrack(it); showSubtitlePicker = false },
            onOff = { viewModel.clearSubtitles(); showSubtitlePicker = false },
            onDismiss = { showSubtitlePicker = false },
        )
    }
}

@Composable
private fun TvPlayerControls(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleTrackClick: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(32.dp),
    ) {
        Button(onClick = onBack) { Text("Back") }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "${formatPositionMs(uiState.positionMs)} / ${formatPositionMs(uiState.durationMs)}",
            color = Color.White,
        )
        uiState.errorMessage?.let { Text(text = it, color = Color.Red) }

        Spacer(modifier = Modifier.padding(top = 12.dp))

        Row {
            Button(onClick = onSeekBack) { Text("-10s") }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = onPlayPause) { Text(if (uiState.isPlaying) "Pause" else "Play") }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = onSeekForward) { Text("+10s") }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = onAudioTrackClick, enabled = uiState.audioTracks.isNotEmpty()) { Text("Audio") }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = onSubtitleTrackClick) { Text("Subtitles") }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Button(onClick = onOpenExternally) { Text("Open externally") }
        }
    }
}

@Composable
private fun TvTrackPickerDialog(
    title: String,
    tracks: List<TrackOption>,
    offSelected: Boolean,
    onSelect: (String) -> Unit,
    onOff: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MaterialTheme {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1C1B1F))
                    .padding(24.dp),
            ) {
                Text(text = title, color = Color.White)
                Spacer(modifier = Modifier.padding(top = 12.dp))
                LazyColumn {
                    if (onOff != null) {
                        item {
                            Button(onClick = onOff) { Text(if (offSelected) "Off (selected)" else "Off") }
                        }
                    }
                    items(tracks) { track ->
                        Button(onClick = { onSelect(track.id) }) {
                            Text(if (track.isSelected && !offSelected) "${track.label} (selected)" else track.label)
                        }
                    }
                }
            }
        }
    }
}
