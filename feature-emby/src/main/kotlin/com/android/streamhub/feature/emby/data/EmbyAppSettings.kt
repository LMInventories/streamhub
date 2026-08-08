package com.android.streamhub.feature.emby.data

import kotlinx.serialization.Serializable

/**
 * User-customizable preferences for an Emby source that aren't part of signing in
 * (EmbySourceConfig) - separate from that so re-signing-in (or signing out and back in) never
 * touches these. Minimal mirror of JellyfinAppSettings, trimmed to just the two fields the
 * pickers/forced-subtitle logic need this pass (no bitrate cap or home-section-order settings yet
 * for Emby - add them here if/when those features reach parity too).
 */
@Serializable
data class EmbyAppSettings(
    // ISO 639-2 codes (e.g. "eng", "spa") - null means no preference, let the player pick its own
    // default track. Free-text rather than a fixed enum since Emby libraries can carry any
    // language a user's media happens to include.
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
)
