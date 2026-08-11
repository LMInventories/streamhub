package com.android.streamhub.feature.emby.data

import kotlinx.serialization.Serializable

/**
 * User-customizable preferences for an Emby source that aren't part of signing in
 * (EmbySourceConfig) - separate from that so re-signing-in (or signing out and back in) never
 * touches these. Mirrors JellyfinAppSettings.
 */
@Serializable
data class EmbyAppSettings(
    // ISO 639-2 codes (e.g. "eng", "spa") - null means no preference, let the player pick its own
    // default track. Free-text rather than a fixed enum since Emby libraries can carry any
    // language a user's media happens to include.
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    // Null means unlimited/direct-play - only ever narrows what the server sends, never forces
    // transcoding for content that's already under the cap.
    val maxStreamingBitrateMbps: Int? = null,
    val hiddenLibraryIds: Set<String> = emptySet(),
    // Ordered section keys for the Emby home screen - "continue_watching", "next_up",
    // "favourites", "library:<id>" for each library. Empty means "use the default order" (the
    // order EmbyHomeContent has always rendered in); once the user reorders, this becomes the
    // source of truth and any section missing from it (a newly added library, most likely) is
    // appended at the end rather than silently dropped.
    val homeSectionOrder: List<String> = emptyList(),
)
