package com.android.streamhub.feature.jellyfin.data

import kotlinx.serialization.Serializable

enum class JellyfinLibraryType { MOVIES, TV_SHOWS }

data class JellyfinLibraryInfo(
    val id: String,
    val name: String,
    val type: JellyfinLibraryType,
)

// @Serializable (not just a plain enum) since JellyfinItemInfo below is persisted as JSON by
// JellyfinHomeCacheRepository - kotlinx.serialization needs every type in that graph annotated.
@Serializable
enum class JellyfinItemType { MOVIE, SERIES, SEASON, EPISODE, OTHER }

enum class JellyfinSortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_ADDED_NEWEST("Recently Added"),
    RATING_HIGHEST("Highest Rated"),
    RELEASE_DATE_NEWEST("Newest Release"),
}

/** Distinct genres/production years available within one library - populated once per library-screen visit to back the Genre/Year filter dropdowns. See JellyfinBrowseRepository.getLibraryFilterOptions for how each list is sourced. */
data class JellyfinLibraryFilterOptions(
    val genres: List<String>,
    val years: List<Int>,
)

@Serializable
data class JellyfinCastMember(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
)

/**
 * A subtitle stream muxed into the media source - [index] is the stream's own index within it,
 * needed to identify it back to the server (not an ExoPlayer track group index). [language] is
 * the raw ISO code (e.g. "eng"), separate from the human-readable [label] - playback selects a
 * track by feeding this straight into ExoPlayer's own preferred-text-language mechanism, the same
 * one the app-wide subtitle language setting already uses. [isForced] mirrors the server's own
 * "forced" flag (dialogue/on-screen-text translations meant to show even with subtitles otherwise
 * off, e.g. the one non-English scene in an otherwise-English film) - see
 * JellyfinMediaSource.resolveSubtitlePreference for how it changes default selection.
 */
@Serializable
data class JellyfinSubtitleTrackInfo(
    val index: Int,
    val label: String,
    val language: String? = null,
    val isForced: Boolean = false,
)

/** An audio stream muxed into the media source - same shape/purpose as JellyfinSubtitleTrackInfo above, plus [isDefault] since (unlike subtitles) audio always needs a track selected, so hydrating a picker's default needs to know which one the server itself considers default. */
@Serializable
data class JellyfinAudioTrackInfo(
    val index: Int,
    val label: String,
    val language: String? = null,
    val isDefault: Boolean = false,
)

/** One alternate encode/rip of the same library item (Jellyfin's own "Version" concept) - [id] is the MediaSourceInfo id fed back into getStreamUrl to select it. */
@Serializable
data class JellyfinVersionInfo(
    val id: String,
    val label: String,
)

/**
 * The server's own analyzed scrubbing-preview sprite sheets for one media source (Jellyfin 10.9+,
 * "Trickplay" - only present once the server has actually run analysis for this item). [width]/
 * [height] are one individual thumbnail's pixel dimensions; [tileGridColumns]/[tileGridRows] are
 * how many thumbnails are packed into one sprite-sheet image (so a client can compute both which
 * image a given playback position falls in and where within it, without a per-position network
 * round trip). [intervalMs] is the time between consecutive thumbnails.
 */
@Serializable
data class JellyfinTrickplayInfo(
    val mediaSourceId: String,
    val width: Int,
    val height: Int,
    val tileGridColumns: Int,
    val tileGridRows: Int,
    val thumbnailCount: Int,
    val intervalMs: Int,
)

@Serializable
data class JellyfinItemInfo(
    val id: String,
    val name: String,
    val type: JellyfinItemType,
    val overview: String?,
    val productionYear: Int?,
    val communityRating: Float?,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val primaryImageUrl: String?,
    val backdropImageUrl: String?,
    // The episode's own scene-grab image, bypassing the series-poster override primaryImageUrl
    // applies for episodes (see toItemInfo's comment) - null for non-episodes. Default null for
    // cache-compatibility, same reasoning as logoImageUrl below.
    val episodeThumbnailUrl: String? = null,
    // Pre-formatted premiere/air date (e.g. "Aug 15, 2023") from Jellyfin's PremiereDate - null
    // when the server has no date for this item. Default null for the same cache-compatibility
    // reason as episodeThumbnailUrl above.
    val premiereDateLabel: String? = null,
    // Episode count for a season item; null for every other item type. Default null for the same
    // cache-compatibility reason as episodeThumbnailUrl above.
    val childCount: Int? = null,
    // Unwatched episode count for a season item (0 once fully watched); null for every other item
    // type. Default null for the same cache-compatibility reason as episodeThumbnailUrl above.
    val unplayedItemCount: Int? = null,
    // Default null (not just nullable) - JellyfinHomeCacheRepository persists this whole type as
    // JSON, and a cache written before this field existed needs to still decode successfully.
    val logoImageUrl: String? = null,
    // Default null for the same cache-compatibility reason as logoImageUrl above. Only populated
    // when the caller requested ItemFields.MEDIA_STREAMS (currently just search()) - other callers
    // simply get null here rather than paying for MediaStreams data nothing renders yet. Both
    // width and height are needed (not just height) since resolutionLabel checks either dimension
    // - a true 4K source cropped to a cinematic aspect ratio can have a height well under 2160.
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    // Human-readable stream summaries (e.g. "1080p H264", "5.1 English AC3") for the detail
    // screen's Video/Audio rows - same MediaStreams fetch and cache-compatibility reasoning as
    // videoHeight/logoImageUrl above.
    val videoLabel: String? = null,
    val audioLabel: String? = null,
    val subtitleTracks: List<JellyfinSubtitleTrackInfo> = emptyList(),
    // Default empty for the same cache-JSON-compatibility reason as episodeThumbnailUrl above.
    val audioTracks: List<JellyfinAudioTrackInfo> = emptyList(),
    val videoVersions: List<JellyfinVersionInfo> = emptyList(),
    val trickplayInfo: JellyfinTrickplayInfo? = null,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    // Episode/season number within its parent - null for movies and series themselves.
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val isFavorite: Boolean,
    // Distinct from playedPercentage below - Jellyfin tracks "fully watched" as its own flag
    // rather than inferring it from percentage, so a "Mark Watched" toggle needs this directly
    // rather than checking playedPercentage >= 100.
    val isPlayed: Boolean = false,
    val playedPercentage: Float?,
    val resumePositionTicks: Long,
    val cast: List<JellyfinCastMember>,
    // Crew/guest-star/tags/studios/external-links are all default-empty for the same cache-JSON
    // compatibility reason as episodeThumbnailUrl/logoImageUrl above.
    //
    // An episode's own people payload doesn't repeat the series' regular cast at all (that's what
    // `cast` already covers, populated for movies/series) - just its own director(s) and any
    // episode-specific guest stars, which is exactly what crew/guestStars hold.
    val crew: List<JellyfinCastMember> = emptyList(),
    val guestStars: List<JellyfinCastMember> = emptyList(),
    val tags: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    // (link label, URL) pairs - e.g. IMDb/Trakt - straight from Jellyfin's own externalUrls, not
    // hand-built from providerIds.
    val externalLinks: List<Pair<String, String>> = emptyList(),
)

/**
 * One renderable "title + row of posters" section - Continue Watching/Next Up/Favourites/each
 * library's Latest all share this same shape, so JellyfinHomeContent can render them from one
 * ordered list instead of a hardcoded sequence of near-identical blocks. Lives here (not in
 * JellyfinHomeViewModel.kt) so JellyfinHomeCacheRepository can persist it as JSON alongside the
 * other @Serializable models in this file, without home/ and data/ depending on each other in
 * both directions.
 */
@Serializable
data class JellyfinHomeSection(
    val key: String,
    val title: String,
    val items: List<JellyfinItemInfo>,
    val hasSeeAll: Boolean,
)
