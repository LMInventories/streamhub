package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
 * Bare live-preview of the focused channel - no mute/fullscreen buttons and no info overlay
 * drawn on top of it (that used to sit here, but it doubled up with the fullscreen overlay this
 * same tap now expands into, and the buttons meant creating a second ExoPlayer instance just to
 * jump to a separate full-screen route, which had to fully rebuffer from scratch - slow, and
 * pointless when this exact player is already playing the exact same stream). The whole box is
 * tappable now instead: [onTap] expands this same player in place, see
 * LiveTvScreenPhone/Tv's fullscreen overlay.
 */
@Composable
fun MiniPlayerPreview(
    exoPlayer: ExoPlayer,
    uiState: PlayerUiState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black).clickable(onClick = onTap)) {
        VideoSurface(exoPlayer = exoPlayer, aspectMode = VideoAspectMode.FIT, modifier = Modifier.fillMaxSize())

        if (uiState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp))
        }
    }
}
