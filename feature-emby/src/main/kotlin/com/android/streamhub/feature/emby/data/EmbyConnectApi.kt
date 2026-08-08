package com.android.streamhub.feature.emby.data

import okhttp3.HttpUrl
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit client for Emby Connect (connect.emby.media) - a separate interface from EmbyApi
 * because it's a fixed-base-URL cloud service, not a per-server one; see
 * EmbyConnectRemoteDataSource's doc for the full flow this backs.
 */
interface EmbyConnectApi {

    @POST("service/user/authenticate")
    suspend fun authenticate(
        @HeaderMap headers: Map<String, String>,
        @Body request: EmbyConnectAuthRequest,
    ): EmbyConnectAuthResult

    @GET("service/servers")
    suspend fun getServers(
        @HeaderMap headers: Map<String, String>,
        @Query("userId") userId: String,
    ): List<EmbyConnectServerDto>

    // This one call targets whatever server the user picked in step 2, not the fixed
    // connect.emby.media base this interface's Retrofit instance is otherwise built with - @Url
    // supplies the full absolute URL and overrides the base entirely for this call, per Retrofit's
    // contract for @Url parameters. @Query params still get appended onto it as normal.
    @GET
    suspend fun exchangeToken(
        @Url url: HttpUrl,
        @HeaderMap headers: Map<String, String>,
        @Query("ConnectUserId") connectUserId: String,
        @Query("format") format: String = "json",
    ): EmbyConnectExchangeResult
}
