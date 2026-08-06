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

/**
 * SD/HD/FHD/4K as explicitly requested - not the "1080p"-style label some reference apps use.
 * Checked against width as well as height (either crossing a tier's threshold qualifies) because
 * a true 4K/UHD source (3840+ wide) mastered at a cinematic aspect ratio (2.35:1, 2.39:1, etc.)
 * is commonly cropped to a height well under 2160 - height alone would misclassify those as FHD
 * despite the source genuinely being 4K.
 */
fun resolutionLabel(width: Int, height: Int): String = when {
    width >= 3840 || height >= 2160 -> "4K"
    width >= 1920 || height >= 1080 -> "FHD"
    width >= 1280 || height >= 720 -> "HD"
    width > 0 || height > 0 -> "SD"
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

/** Friendly codec name from ExoPlayer's raw sample MIME type subtype (e.g. "video/hevc" -> "H.265") - falls back to the raw subtype uppercased for anything not explicitly mapped, rather than showing nothing for a codec this list just hasn't been taught about yet. */
fun codecLabel(sampleMimeType: String?): String {
    val subtype = sampleMimeType?.substringAfter('/') ?: return ""
    return when (subtype) {
        "avc" -> "H.264"
        "hevc" -> "H.265"
        "av01" -> "AV1"
        "vp9" -> "VP9"
        "vp8" -> "VP8"
        "mp4a-latm" -> "AAC"
        "ac3" -> "AC3"
        "eac3" -> "E-AC3"
        "opus" -> "Opus"
        "flac" -> "FLAC"
        "raw" -> "PCM"
        else -> subtype.uppercase()
    }
}

/** Mbps once the stream clears 1 Mbps (the common case for video), kbps below that (the common case for audio) - matches how bitrate is conventionally reported for each. */
fun bitrateLabel(bitrateBps: Int): String {
    if (bitrateBps <= 0) return ""
    val mbps = bitrateBps / 1_000_000f
    return if (mbps >= 1f) "%.1f Mbps".format(mbps) else "${bitrateBps / 1000} kbps"
}
