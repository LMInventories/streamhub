package com.android.streamhub.feature.player

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatClockTime(epochMs: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

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

/** Elapsed/total within the current programme's scheduled EPG slot, not the stream's own playback position - a live broadcast isn't seekable, so this is purely informational. Use with formatPositionMs (PlayerTimeFormat.kt) to render as text. */
fun liveProgramProgress(nowStartMs: Long, nowEndMs: Long, currentTimeMs: Long): Pair<Long, Long> {
    val total = (nowEndMs - nowStartMs).coerceAtLeast(1L)
    val elapsed = (currentTimeMs - nowStartMs).coerceIn(0L, total)
    return elapsed to total
}
