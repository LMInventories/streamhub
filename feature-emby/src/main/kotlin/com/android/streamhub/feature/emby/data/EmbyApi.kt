package com.android.streamhub.feature.emby.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Hand-rolled Emby REST client - Emby has proper multi-path resources (like Jellyfin, since
 * Jellyfin forked from Emby's predecessor), not Xtream's single-action-param shape, so this is
 * one method per resource path rather than one endpoint switched by an `action` query param.
 * Every authenticated call takes `@HeaderMap headers` rather than individual `@Header` params,
 * so EmbyRemoteDataSource.authHeaders() stays the single source of truth for what gets sent -
 * see its doc for why (unverified wire format, no live server to test against).
 */
interface EmbyApi {

    // Requires the client-identification header (see EmbyRemoteDataSource.authHeaders) even
    // though no token exists yet - Emby's server uses it to associate the resulting session with
    // this client/device, the same as Jellyfin's AuthenticateUserByName.
    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @HeaderMap headers: Map<String, String>,
        @Body request: EmbyAuthRequest,
    ): EmbyAuthResult

    // Deliberately unauthenticated (no headers param) - used before sign-in to validate a server
    // URL, the same role Jellyfin's own getPublicSystemInfo call plays.
    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): EmbyPublicSystemInfo

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(
        @HeaderMap headers: Map<String, String>,
        @Path("userId") userId: String,
    ): EmbyItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @HeaderMap headers: Map<String, String>,
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        // Comma-separated BaseItemKind-equivalent names (e.g. "Movie,Series") - Emby's own list-param convention.
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean? = null,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortOrder") sortOrder: String? = null,
        @Query("StartIndex") startIndex: Int? = null,
        @Query("Limit") limit: Int? = null,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("Fields") fields: String? = null,
    ): EmbyItemsResponse

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @HeaderMap headers: Map<String, String>,
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
    ): EmbyItemDto

    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItems(
        @HeaderMap headers: Map<String, String>,
        @Path("userId") userId: String,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Limit") limit: Int? = null,
    ): EmbyItemsResponse

    // UNVERIFIED - modeled as returning a bare JSON array rather than the {Items:[...]} envelope
    // every other list endpoint here uses, per community documentation of Emby's /Items/Latest.
    // No live server to confirm this against; flagged in the Phase 1 plan as the single most
    // likely wire-format bug. If wrong, the fix is scoped to this method's return type plus its
    // one call site (EmbyBrowseRepository.getLatestMedia).
    @GET("Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @HeaderMap headers: Map<String, String>,
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("Limit") limit: Int? = null,
    ): List<EmbyItemDto>

    @GET("Shows/NextUp")
    suspend fun getNextUp(
        @HeaderMap headers: Map<String, String>,
        @Query("UserId") userId: String,
        @Query("SeriesId") seriesId: String? = null,
        @Query("Limit") limit: Int? = null,
    ): EmbyItemsResponse

    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @HeaderMap headers: Map<String, String>,
        @Path("seriesId") seriesId: String,
        @Query("UserId") userId: String,
    ): EmbyItemsResponse

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @HeaderMap headers: Map<String, String>,
        @Path("seriesId") seriesId: String,
        @Query("UserId") userId: String,
        @Query("SeasonId") seasonId: String? = null,
    ): EmbyItemsResponse

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @HeaderMap headers: Map<String, String>,
        @Path("itemId") itemId: String,
        @Body request: EmbyPlaybackInfoRequest,
    ): EmbyPlaybackInfoResponse

    // Playback-report POSTs return Response<Unit> rather than a parsed body - never call .body()
    // on these, only check success/failure (and even that is best-effort, see
    // EmbyBrowseRepository's report* functions, which runCatching-wrap every call).
    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @HeaderMap headers: Map<String, String>,
        @Body request: EmbyPlaybackStartRequest,
    ): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @HeaderMap headers: Map<String, String>,
        @Body request: EmbyPlaybackProgressRequest,
    ): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @HeaderMap headers: Map<String, String>,
        @Body request: EmbyPlaybackStopRequest,
    ): Response<Unit>

    @POST("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markPlayedItem(
        @HeaderMap headers: Map<String, String>,
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
    ): Response<Unit>
}
