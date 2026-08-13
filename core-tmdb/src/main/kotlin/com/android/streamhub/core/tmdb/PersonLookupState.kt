package com.android.streamhub.core.tmdb

/**
 * Shared side-channel state shape for "cast member name tapped -> resolving a TMDB person id"
 * across Jellyfin's and Emby's detail ViewModels (JellyfinItemDetailViewModel,
 * JellyfinSeriesDetailViewModel, EmbyItemDetailViewModel, EmbySeriesDetailViewModel) - kept here,
 * shared, rather than 4 near-identical private sealed classes, since the shape itself carries no
 * source-specific logic.
 */
sealed class PersonLookupState {
    data object Idle : PersonLookupState()
    data object Loading : PersonLookupState()
    data class Found(val tmdbPersonId: Int) : PersonLookupState()
    data object NotFound : PersonLookupState()
}
