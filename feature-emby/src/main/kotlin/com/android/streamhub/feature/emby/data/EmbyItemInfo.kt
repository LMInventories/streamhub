package com.android.streamhub.feature.emby.data

enum class EmbyLibraryType { MOVIES, TV_SHOWS }

data class EmbyLibraryInfo(
    val id: String,
    val name: String,
    val type: EmbyLibraryType,
)

enum class EmbyItemType { MOVIE, SERIES, SEASON, EPISODE, OTHER }

enum class EmbySortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_ADDED_NEWEST("Recently Added"),
    RATING_HIGHEST("Highest Rated"),
    RELEASE_DATE_NEWEST("Newest Release"),
}

data class EmbyCastMember(
    val id: String,
    val name: String,
    val role: String?,
)

/**
 * A subtitle stream muxed into the media source - [index] is the stream's own index within it,
 * needed to identify it back to the server (not an ExoPlayer track group index). [language] is
 * the raw ISO code (e.g. "eng"), separate from the human-readable [label] - playback selects a
 * track by feeding this straight into ExoPlayer's own preferred-text-language mechanism, the same
 * one the app-wide subtitle language setting already uses. [isForced] mirrors the server's own
 * "forced" flag (dialogue/on-screen-text translations meant to show even with subtitles otherwise
 * off, e.g. the one non-English scene in an otherwise-English film) - see
 * EmbyMediaSource.resolveSubtitlePreference for how it changes default selection. Mirrors
 * JellyfinSubtitleTrackInfo exactly.
 */
data class EmbySubtitleTrackInfo(
    val index: Int,
    val label: String,
    val language: String? = null,
    val isForced: Boolean = false,
)

/** An audio stream muxed into the media source - same shape/purpose as EmbySubtitleTrackInfo above, plus [isDefault] since (unlike subtitles) audio always needs a track selected, so hydrating a picker's default needs to know which one the server itself considers default. */
data class EmbyAudioTrackInfo(
    val index: Int,
    val label: String,
    val language: String? = null,
    val isDefault: Boolean = false,
)

/** One alternate encode/rip of the same library item (Emby's own "Version" concept) - [id] is the MediaSourceInfo id fed back into getStreamUrl to select it. */
data class EmbyVersionInfo(
    val id: String,
    val label: String,
)

/**
 * The server's own analyzed scrubbing-preview sprite sheets for one media source - only present
 * once the server has actually run analysis for this item. [width]/[height] are one individual
 * thumbnail's pixel dimensions; [tileGridColumns]/[tileGridRows] are how many thumbnails are
 * packed into one sprite-sheet image (so a client can compute both which image a given playback
 * position falls in and where within it, without a per-position network round trip). [intervalMs]
 * is the time between consecutive thumbnails. Mirrors JellyfinTrickplayInfo exactly - see
 * EmbyModels.EmbyItemDto's `trickplay` field doc for why this whole feature is best-effort/
 * unverified for Emby.
 */
data class EmbyTrickplayInfo(
    val mediaSourceId: String,
    val width: Int,
    val height: Int,
    val tileGridColumns: Int,
    val tileGridRows: Int,
    val thumbnailCount: Int,
    val intervalMs: Int,
)

/**
 * App-facing flattened model mirroring feature-jellyfin's JellyfinItemInfo. Originally trimmed
 * for the lean MVP's scope (no favorite flag, no track/version pickers, no trickplay) - this pass
 * fills those back in to reach parity, see JellyfinItemInfo's own field docs for the reasoning
 * behind each (mirrored here, not repeated verbatim).
 */
data class EmbyItemInfo(
    val id: String,
    val name: String,
    val type: EmbyItemType,
    val overview: String?,
    val productionYear: Int?,
    val communityRating: Float?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val primaryImageUrl: String?,
    val backdropImageUrl: String?,
    // The episode's own scene-grab image, bypassing the series-poster override primaryImageUrl
    // applies for episodes (see EmbyBrowseRepository.toItemInfo's comment) - null for
    // non-episodes.
    val episodeThumbnailUrl: String? = null,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    // Episode/season number within its parent - null for movies and series themselves.
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val isFavorite: Boolean = false,
    // Distinct from playedPercentage below - Emby tracks "fully watched" as its own flag rather
    // than inferring it from percentage, so a "Mark Watched" toggle needs this directly rather
    // than checking playedPercentage >= 100. Mirrors JellyfinItemInfo.isPlayed.
    val isPlayed: Boolean = false,
    val playedPercentage: Float?,
    val resumePositionTicks: Long,
    val cast: List<EmbyCastMember>,
    // Episode count for a season item; null for every other item type.
    val childCount: Int? = null,
    val subtitleTracks: List<EmbySubtitleTrackInfo> = emptyList(),
    val audioTracks: List<EmbyAudioTrackInfo> = emptyList(),
    // Only surfaced when there's an actual choice (mediaSources.size > 1) - a single-version item
    // (the overwhelming majority) just keeps using the plain read-only Video row instead.
    val videoVersions: List<EmbyVersionInfo> = emptyList(),
    val trickplayInfo: EmbyTrickplayInfo? = null,
)

/**
 * One renderable "title + row of posters" section - Continue Watching/Next Up/Favourites/each
 * library's Latest all share this same shape, so EmbyHomeContent can render them from one ordered
 * list instead of a hardcoded sequence of near-identical blocks. Mirrors JellyfinHomeSection
 * exactly, including living here (not in EmbyHomeViewModel.kt) for the same reason: keeping it in
 * the data layer alongside the other models in this file, not tangled up with home/'s own
 * UI-facing code.
 */
data class EmbyHomeSection(
    val key: String,
    val title: String,
    val items: List<EmbyItemInfo>,
    val hasSeeAll: Boolean,
)

/** Stable keys for EmbyAppSettings.homeSectionOrder - kept in one place so a future reorder screen and the home screen agree on what a key means. Mirrors JellyfinHomeSectionKeys exactly. */
object EmbyHomeSectionKeys {
    const val CONTINUE_WATCHING = "continue_watching"
    const val NEXT_UP = "next_up"
    const val FAVOURITES = "favourites"
    fun library(libraryId: String) = "library:$libraryId"
}
