package com.android.streamhub.core.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TmdbPersonSearchResponse(
    @SerialName("results") val results: List<TmdbPersonSearchResult> = emptyList(),
)

@Serializable
internal data class TmdbPersonSearchResult(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
internal data class TmdbPersonDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("biography") val biography: String? = null,
    @SerialName("birthday") val birthday: String? = null,
    @SerialName("deathday") val deathday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
internal data class TmdbCombinedCreditsResponse(
    @SerialName("cast") val cast: List<TmdbCreditDto> = emptyList(),
)

// TMDB's combined_credits endpoint mixes movie-shaped and TV-shaped entries in one array,
// disambiguated only by mediaType ("movie" vs "tv") - title/releaseDate are populated for movies,
// name/firstAirDate for TV, and TmdbRepository picks whichever pair is non-null based on that flag.
@Serializable
internal data class TmdbCreditDto(
    @SerialName("id") val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)
