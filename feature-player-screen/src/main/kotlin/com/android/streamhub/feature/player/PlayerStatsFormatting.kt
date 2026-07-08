package com.android.streamhub.feature.player

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatClockTime(epochMs: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** Elapsed/total within the current programme's scheduled EPG slot, not the stream's own playback position - a live broadcast isn't seekable, so this is purely informational. Use with core-player's formatPositionMs to render as text. */
fun liveProgramProgress(nowStartMs: Long, nowEndMs: Long, currentTimeMs: Long): Pair<Long, Long> {
    val total = (nowEndMs - nowStartMs).coerceAtLeast(1L)
    val elapsed = (currentTimeMs - nowStartMs).coerceIn(0L, total)
    return elapsed to total
}
