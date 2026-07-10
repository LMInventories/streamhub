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

@Serializable
data class JellyfinCastMember(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
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
    // Default null (not just nullable) - JellyfinHomeCacheRepository persists this whole type as
    // JSON, and a cache written before this field existed needs to still decode successfully.
    val logoImageUrl: String? = null,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    // Episode/season number within its parent - null for movies and series themselves.
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val isFavorite: Boolean,
    val playedPercentage: Float?,
    val resumePositionTicks: Long,
    val cast: List<JellyfinCastMember>,
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
