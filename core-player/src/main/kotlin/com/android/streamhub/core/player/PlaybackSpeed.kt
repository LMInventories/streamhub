package com.android.streamhub.core.player

/** The standard speed steps every mainstream player offers - shared by both the phone and TV speed pickers so they can't drift out of sync with each other. */
val PLAYBACK_SPEED_OPTIONS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** "Normal" for 1x (matches every other player's convention) rather than a redundant "1x". */
fun playbackSpeedLabel(speed: Float): String {
    if (speed == 1f) return "Normal"
    val trimmed = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "${trimmed}x"
}
