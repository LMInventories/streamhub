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
 *
 * Used by Live TV/multiview, where "paused" isn't really a state that exists the same way - see
 * [KeepScreenOnWhileOpen] for the on-demand movie/episode player, which needs different behavior.
 */
@Composable
fun KeepScreenOnWhilePlaying(isPlaying: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Same underlying mechanism as [KeepScreenOnWhilePlaying], but stays on for as long as this
 * screen is composed, regardless of play/pause state - for the on-demand movie/episode player
 * specifically, where a paused-but-still-open screen means "I stepped away for a moment," not
 * "I'm done watching." [KeepScreenOnWhilePlaying]'s play-state-gated behavior let the screen dim
 * and the TV's screensaver activate during a pause, and with no other wake lock held, a long
 * enough pause let Android TV kill the app process to reclaim memory - invisible to the user
 * until they returned to find playback had silently restarted from a fresh process, with any
 * in-player subtitle choice reset back to its pre-playback default (see
 * PlayerViewModel.reapplyExplicitSubtitleChoice for the other half of that fix).
 */
@Composable
fun KeepScreenOnWhileOpen() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
