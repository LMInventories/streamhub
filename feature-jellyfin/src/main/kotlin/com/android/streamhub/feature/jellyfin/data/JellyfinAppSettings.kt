package com.android.streamhub.feature.jellyfin.data

import kotlinx.serialization.Serializable

/**
 * User-customizable preferences for a Jellyfin source that aren't part of signing in
 * (JellyfinSourceConfig) - separate from that so re-signing-in (or signing out and back in) never
 * touches these.
 */
@Serializable
data class JellyfinAppSettings(
    // ISO 639-2 codes (e.g. "eng", "spa") - null means no preference, let the player pick its own
    // default track. Free-text rather than a fixed enum since Jellyfin libraries can carry any
    // language a user's media happens to include.
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    // Null means unlimited/direct-play - only ever narrows what the server sends, never forces
    // transcoding for content that's already under the cap.
    val maxStreamingBitrateMbps: Int? = null,
    val hiddenLibraryIds: Set<String> = emptySet(),
    // Ordered section keys for the Jellyfin home screen - "continue_watching", "next_up",
    // "favourites", "library:<id>" for each library. Empty means "use the default order" (the
    // order JellyfinHomeContent has always rendered in); once the user reorders, this becomes the
    // source of truth and any section missing from it (a newly added library, most likely) is
    // appended at the end rather than silently dropped.
    val homeSectionOrder: List<String> = emptyList(),
)

/** Stable keys for JellyfinAppSettings.homeSectionOrder - kept in one place so the reorder screen and the home screen agree on what a key means. */
object JellyfinHomeSectionKeys {
    const val CONTINUE_WATCHING = "continue_watching"
    const val NEXT_UP = "next_up"
    const val FAVOURITES = "favourites"
    fun library(libraryId: String) = "library:$libraryId"
}
