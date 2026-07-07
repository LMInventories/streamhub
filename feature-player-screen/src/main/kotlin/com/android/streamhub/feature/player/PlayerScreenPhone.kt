package com.android.streamhub.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.TrackOption
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreenPhone(
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
            delay(4000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        PlayerSurface(player = viewModel.exoPlayer, modifier = Modifier.fillMaxSize())

        if (uiState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            PhonePlayerControls(
                uiState = uiState,
                onBack = onBack,
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onAudioTrackClick = { showAudioPicker = true },
                onSubtitleTrackClick = { showSubtitlePicker = true },
                onOpenExternally = { viewModel.openExternally(context) },
            )
        }
    }

    if (showAudioPicker) {
        TrackPickerBottomSheet(
            title = "Audio",
            tracks = uiState.audioTracks,
            offSelected = false,
            onSelect = { viewModel.selectAudioTrack(it); showAudioPicker = false },
            onOff = null,
            onDismiss = { showAudioPicker = false },
        )
    }
    if (showSubtitlePicker) {
        TrackPickerBottomSheet(
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
private fun PhonePlayerControls(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleTrackClick: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            uiState.errorMessage?.let {
                Text(text = it, color = Color.Red)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Slider(
                value = uiState.positionMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..uiState.durationMs.coerceAtLeast(1L).toFloat(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatPositionMs(uiState.positionMs)} / ${formatPositionMs(uiState.durationMs)}",
                    color = Color.White,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onAudioTrackClick, enabled = uiState.audioTracks.isNotEmpty()) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = "Audio track", tint = Color.White)
                }
                IconButton(onClick = onSubtitleTrackClick) {
                    Icon(Icons.Filled.ClosedCaption, contentDescription = "Subtitles", tint = Color.White)
                }
                IconButton(onClick = onOpenExternally) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open externally", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackPickerBottomSheet(
    title: String,
    tracks: List<TrackOption>,
    offSelected: Boolean,
    onSelect: (String) -> Unit,
    onOff: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn {
            item {
                Text(text = title, modifier = Modifier.padding(16.dp))
            }
            if (onOff != null) {
                item {
                    ListItem(
                        headlineContent = { Text("Off") },
                        trailingContent = {
                            if (offSelected) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        modifier = Modifier.clickable(onClick = onOff),
                    )
                }
            }
            items(tracks) { track ->
                ListItem(
                    headlineContent = { Text(track.label) },
                    trailingContent = {
                        if (track.isSelected && !offSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable { onSelect(track.id) },
                )
            }
        }
    }
}
