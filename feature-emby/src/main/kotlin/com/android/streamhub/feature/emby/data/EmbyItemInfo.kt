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
 * App-facing flattened model mirroring feature-jellyfin's JellyfinItemInfo, trimmed for this
 * pass's lean MVP scope: no subtitle/audio/version track fields (no picker UI yet), no trickplay
 * (deferred), no favorite flag (deferred).
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
    val playedPercentage: Float?,
    val resumePositionTicks: Long,
    val cast: List<EmbyCastMember>,
    // Episode count for a season item; null for every other item type.
    val childCount: Int? = null,
)
