package com.android.streamhub.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks

/** UI-friendly representation of one selectable audio or subtitle track. */
data class TrackOption(
    val groupIndex: Int,
    val trackIndexInGroup: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
) {
    val id: String get() = "$groupIndex:$trackIndexInGroup"
}

/**
 * Pure mapping from Media3's [Tracks] to the flat [TrackOption] list the UI renders, filtered to
 * one track type (audio or text). Kept as a standalone function so it's cheaply unit-testable
 * without spinning up a real player.
 */
fun Tracks.toTrackOptions(trackType: @C.TrackType Int): List<TrackOption> {
    val options = mutableListOf<TrackOption>()
    groups.forEachIndexed { groupIndex, group ->
        if (group.type != trackType) return@forEachIndexed
        for (trackIndexInGroup in 0 until group.length) {
            val format = group.getTrackFormat(trackIndexInGroup)
            options += TrackOption(
                groupIndex = groupIndex,
                trackIndexInGroup = trackIndexInGroup,
                label = format.labelFor(trackType),
                language = format.language,
                isSelected = group.isTrackSelected(trackIndexInGroup),
            )
        }
    }
    return options
}

private fun Format.labelFor(trackType: @C.TrackType Int): String {
    label?.let { return it }
    language?.let { return it.uppercase() }
    val fallbackPrefix = if (trackType == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitle"
    return "$fallbackPrefix ${id ?: "track"}"
}
