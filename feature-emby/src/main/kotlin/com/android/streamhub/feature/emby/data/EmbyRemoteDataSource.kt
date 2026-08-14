package com.android.streamhub.feature.emby.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.android.streamhub.feature.emby.di.EmbyJson
import com.android.streamhub.feature.emby.di.EmbyOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject

/**
 * The user's Emby server URL is only known at runtime (entered in Settings), so the Retrofit
 * instance can't be a single Hilt-provided singleton with a fixed base URL - build one per base
 * URL instead, cached so repeat calls against the same server don't rebuild it. Exact shape of
 * feature-iptv's XtreamRemoteDataSource (no official Emby SDK exists to build against instead,
 * unlike feature-jellyfin's org.jellyfin.sdk.Jellyfin factory).
 */
class EmbyRemoteDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @EmbyOkHttpClient private val okHttpClient: OkHttpClient,
    @EmbyJson private val json: Json,
) {
    private val apiCache = mutableMapOf<String, EmbyApi>()

    private fun apiFor(baseUrl: String): EmbyApi = apiCache.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EmbyApi::class.java)
    }

    /**
     * Builds the header(s) sent on every authenticated call - the single riskiest guess in this
     * whole module, since no live Emby server is available to verify wire format against (see
     * the Phase 1 plan's Verification section). Community documentation and other unofficial
     * Emby clients describe an `X-Emby-Authorization` header carrying a `MediaBrowser` scheme
     * string (client/device identity, plus `Token="..."` once signed in) - the same shape
     * Jellyfin's own Authorization header uses, since Jellyfin inherited this convention when it
     * forked from Emby's predecessor - but some servers/proxies instead key off a bare
     * `X-Emby-Token` header. Sending BOTH maximizes the odds one of them is what this particular
     * server actually expects, and - critically - keeps that guesswork isolated to this one
     * function: if sign-in 401s, or sign-in succeeds but later calls don't, the fix is a one-file
     * change here rather than a hunt through every call site that authenticates.
     */
    @SuppressLint("HardwareIds")
    fun authHeaders(token: String? = null): Map<String, String> {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val authorization = buildString {
            append("MediaBrowser Client=\"StreamHub\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"1.0.0\"")
            if (token != null) append(", Token=\"$token\"")
        }
        val headers = mutableMapOf("X-Emby-Authorization" to authorization)
        if (token != null) headers["X-Emby-Token"] = token
        return headers
    }

    suspend fun authenticateByName(baseUrl: String, username: String, password: String): EmbyAuthResult =
        apiFor(baseUrl).authenticateByName(authHeaders(), EmbyAuthRequest(username = username, pw = password))

    suspend fun getPublicSystemInfo(baseUrl: String): EmbyPublicSystemInfo =
        apiFor(baseUrl).getPublicSystemInfo()

    suspend fun getUserViews(baseUrl: String, token: String, userId: String): EmbyItemsResponse =
        apiFor(baseUrl).getUserViews(authHeaders(token), userId)

    suspend fun getItems(
        baseUrl: String,
        token: String,
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        startIndex: Int? = null,
        limit: Int? = null,
        searchTerm: String? = null,
        fields: String? = null,
        isFavorite: Boolean? = null,
        genres: String? = null,
        years: String? = null,
    ): EmbyItemsResponse = apiFor(baseUrl).getItems(
        headers = authHeaders(token),
        userId = userId,
        parentId = parentId,
        includeItemTypes = includeItemTypes,
        recursive = recursive,
        sortBy = sortBy,
        sortOrder = sortOrder,
        startIndex = startIndex,
        limit = limit,
        searchTerm = searchTerm,
        fields = fields,
        isFavorite = isFavorite,
        genres = genres,
        years = years,
    )

    suspend fun getItem(baseUrl: String, token: String, userId: String, itemId: String): EmbyItemDto =
        apiFor(baseUrl).getItem(authHeaders(token), userId, itemId)

    suspend fun getResumeItems(
        baseUrl: String,
        token: String,
        userId: String,
        includeItemTypes: String? = null,
        limit: Int? = null,
    ): EmbyItemsResponse = apiFor(baseUrl).getResumeItems(authHeaders(token), userId, includeItemTypes, limit)

    suspend fun getLatestItems(
        baseUrl: String,
        token: String,
        userId: String,
        parentId: String? = null,
        limit: Int? = null,
    ): List<EmbyItemDto> = apiFor(baseUrl).getLatestItems(authHeaders(token), userId, parentId, limit)

    suspend fun getNextUp(
        baseUrl: String,
        token: String,
        userId: String,
        seriesId: String? = null,
        limit: Int? = null,
    ): EmbyItemsResponse = apiFor(baseUrl).getNextUp(authHeaders(token), userId, seriesId, limit)

    suspend fun getSeasons(baseUrl: String, token: String, seriesId: String, userId: String): EmbyItemsResponse =
        apiFor(baseUrl).getSeasons(authHeaders(token), seriesId, userId)

    suspend fun getEpisodes(
        baseUrl: String,
        token: String,
        seriesId: String,
        userId: String,
        seasonId: String? = null,
    ): EmbyItemsResponse = apiFor(baseUrl).getEpisodes(authHeaders(token), seriesId, userId, seasonId)

    suspend fun getPlaybackInfo(
        baseUrl: String,
        token: String,
        itemId: String,
        userId: String,
        maxStreamingBitrate: Int? = null,
    ): EmbyPlaybackInfoResponse = apiFor(baseUrl).getPlaybackInfo(
        authHeaders(token),
        itemId,
        EmbyPlaybackInfoRequest(userId = userId, maxStreamingBitrate = maxStreamingBitrate),
    )

    suspend fun reportPlaybackStart(baseUrl: String, token: String, itemId: String): Response<Unit> =
        apiFor(baseUrl).reportPlaybackStart(authHeaders(token), EmbyPlaybackStartRequest(itemId = itemId))

    suspend fun reportPlaybackProgress(
        baseUrl: String,
        token: String,
        itemId: String,
        positionTicks: Long,
        isPaused: Boolean,
    ): Response<Unit> = apiFor(baseUrl).reportPlaybackProgress(
        authHeaders(token),
        EmbyPlaybackProgressRequest(itemId = itemId, positionTicks = positionTicks, isPaused = isPaused),
    )

    suspend fun reportPlaybackStopped(baseUrl: String, token: String, itemId: String, positionTicks: Long): Response<Unit> =
        apiFor(baseUrl).reportPlaybackStopped(authHeaders(token), EmbyPlaybackStopRequest(itemId = itemId, positionTicks = positionTicks))

    suspend fun markPlayedItem(baseUrl: String, token: String, userId: String, itemId: String): Response<Unit> =
        apiFor(baseUrl).markPlayedItem(authHeaders(token), userId, itemId)

    suspend fun unmarkPlayedItem(baseUrl: String, token: String, userId: String, itemId: String): Response<Unit> =
        apiFor(baseUrl).unmarkPlayedItem(authHeaders(token), userId, itemId)

    suspend fun markFavoriteItem(baseUrl: String, token: String, userId: String, itemId: String): Response<Unit> =
        apiFor(baseUrl).markFavoriteItem(authHeaders(token), userId, itemId)

    suspend fun unmarkFavoriteItem(baseUrl: String, token: String, userId: String, itemId: String): Response<Unit> =
        apiFor(baseUrl).unmarkFavoriteItem(authHeaders(token), userId, itemId)
}
