package com.android.streamhub.core.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Renders one ExoPlayer's video output. Uses the classic View-based PlayerView via AndroidView
 * rather than media3-ui-compose's PlayerSurface - PlayerSurface has no resize-mode concept, and
 * the 4:3/16:9/Fit/Fill picker needs PlayerView's well-established RESIZE_MODE_* support.
 * Shared between the full-screen player and the Live TV mini-player so both stay in sync with
 * any future player-rendering changes.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    exoPlayer: ExoPlayer,
    aspectMode: VideoAspectMode,
    modifier: Modifier = Modifier,
    useController: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    this.useController = useController
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = aspectMode.resizeMode
            },
        )
    }

    val containerAspectRatio = aspectMode.containerAspectRatio
    if (containerAspectRatio != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.aspectRatio(containerAspectRatio)) {
                content()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}
