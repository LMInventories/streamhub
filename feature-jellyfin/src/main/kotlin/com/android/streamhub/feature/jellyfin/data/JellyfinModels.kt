package com.android.streamhub.feature.jellyfin.data

enum class JellyfinLibraryType { MOVIES, TV_SHOWS }

data class JellyfinLibraryInfo(
    val id: String,
    val name: String,
    val type: JellyfinLibraryType,
)

enum class JellyfinItemType { MOVIE, SERIES, SEASON, EPISODE, OTHER }

enum class JellyfinSortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_ADDED_NEWEST("Recently Added"),
    RATING_HIGHEST("Highest Rated"),
    RELEASE_DATE_NEWEST("Newest Release"),
}

data class JellyfinCastMember(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
)

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
