package com.android.streamhub.core.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Tells the OS a video is actively playing so it doesn't dim/sleep the display or - on Android
 * TV - kick in the screensaver out from under a stream nobody's touched the remote for. Keyed
 * to [isPlaying] specifically (not just "this screen is open") so a paused player still lets the
 * screensaver/screen-off behave normally rather than pinning the display awake indefinitely.
 *
 * View.keepScreenOn is the standard mechanism for this - it sets the same underlying window flag
 * (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) a Window-level call would, but reachable here
 * without needing an Activity reference, just the Compose-hosting View already available via
 * LocalView.
 */
@Composable
fun KeepScreenOnWhilePlaying(isPlaying: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}
