package com.android.streamhub.feature.iptv.livetv

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Small, deliberately duplicated from feature-player-screen's equivalents rather than shared via
// a new feature-to-feature dependency - this app's module architecture keeps feature modules
// independent of each other (cross-feature reuse goes through core-* modules), and these two are
// short, generic, Android-framework-only implementations with no real logic worth centralizing.

/** Standard video-player UX: force landscape while active, restore afterward. */
@Composable
fun LockLandscapeWhileFullscreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity.requestedOrientation = previousOrientation }
    }
}

/** True fullscreen: hides the status bar and nav bar while active, restores both on exit. Swiping from an edge still temporarily reveals them rather than requiring a settings dive to get them back. */
@Composable
fun HideSystemBarsWhileFullscreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousSystemBarsBehavior = controller.systemBarsBehavior

        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousSystemBarsBehavior
        }
    }
}
