package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.android.streamhub.core.player.PlayerUiState
import com.android.streamhub.core.player.VideoAspectMode
import com.android.streamhub.core.player.VideoSurface

/**
 * Small live-preview of the focused channel with mute/fullscreen controls in the corner.
 * Shared between the phone and TV Live TV screens - both use it identically regardless of
 * whether it's positioned above the list (portrait) or top-left of it (landscape).
 */
@Composable
fun MiniPlayerPreview(
    exoPlayer: ExoPlayer,
    uiState: PlayerUiState,
    onToggleMute: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        VideoSurface(exoPlayer = exoPlayer, aspectMode = VideoAspectMode.FIT, modifier = Modifier.fillMaxSize())

        if (uiState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp))
        }

        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)) {
            IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (uiState.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onFullscreen, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
            }
        }
    }
}
