package com.android.streamhub.core.player

import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure formatting helpers for PlayerUiState's stream stats - lives here (not in
 * feature-player-screen) so feature-iptv's own in-place live overlay can render the same badges
 * without a feature-to-feature module dependency, which this app's architecture deliberately
 * avoids (cross-feature reuse goes through core-* modules instead).
 */
fun formatPositionMs(positionMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(positionMs.coerceAtLeast(0L))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** SD/HD/FHD/4K as explicitly requested - not the "1080p"-style label some reference apps use. */
fun resolutionLabel(height: Int): String = when {
    height >= 2160 -> "4K"
    height >= 1080 -> "FHD"
    height >= 720 -> "HD"
    height > 0 -> "SD"
    else -> ""
}

fun frameRateLabel(frameRate: Float): String =
    if (frameRate > 0f) "${frameRate.roundToInt()} FPS" else ""

fun audioChannelsLabel(channelCount: Int): String = when (channelCount) {
    0 -> ""
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$channelCount ch"
}

fun aspectRatioLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return ""
    val ratio = width.toFloat() / height.toFloat()
    return when {
        abs(ratio - 16f / 9f) < 0.05f -> "16:9"
        abs(ratio - 4f / 3f) < 0.05f -> "4:3"
        abs(ratio - 21f / 9f) < 0.08f -> "21:9"
        else -> "%.2f:1".format(ratio)
    }
}
