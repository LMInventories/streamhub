package com.android.streamhub.core.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** TMDB v3 REST API - only the endpoints this app actually uses (person search/detail/credits). */
interface TmdbApi {

    @GET("search/person")
    suspend fun searchPerson(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
    ): TmdbPersonSearchResponse

    @GET("person/{personId}")
    suspend fun getPerson(
        @Path("personId") personId: Int,
        @Query("api_key") apiKey: String,
    ): TmdbPersonDto

    @GET("person/{personId}/combined_credits")
    suspend fun getCombinedCredits(
        @Path("personId") personId: Int,
        @Query("api_key") apiKey: String,
    ): TmdbCombinedCreditsResponse
}
