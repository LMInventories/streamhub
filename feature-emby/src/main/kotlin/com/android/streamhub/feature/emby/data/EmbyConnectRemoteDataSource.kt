package com.android.streamhub.feature.emby.data

import com.android.streamhub.feature.emby.di.EmbyJson
import com.android.streamhub.feature.emby.di.EmbyOkHttpClient
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject

private const val CONNECT_BASE_URL = "https://connect.emby.media/"
private const val APPLICATION_HEADER_VALUE = "StreamHub/1.0.0"

/**
 * Emby Connect (https://connect.emby.media) is Emby's cloud account-linking sign-in system - a
 * user's Connect account can be linked to one or more independently-hosted Emby servers, so
 * signing in with it is a three-step flow rather than a single AuthenticateByName call against
 * one already-known server:
 *  1. [authenticate] the Connect account itself, getting back a Connect-scoped access token/id.
 *  2. [getServers] discovers which server(s) that Connect account is linked to.
 *  3. [exchangeToken] trades the chosen server's AccessKey (from step 2) for a token local to
 *     that server - the result slots directly into EmbySourceConfig, same as a direct sign-in.
 *
 * Unlike EmbyRemoteDataSource (whose base URL is only known at runtime, once a server URL is
 * entered), Connect's base URL never varies, so this is a single Hilt-provided Retrofit instance
 * rather than a per-baseUrl cache.
 *
 * This whole flow's endpoint shapes and field names are confirmed via two independent official
 * sources (dev.emby.media and the MediaBrowser/Emby GitHub wiki agree) - high confidence, unlike
 * several other best-effort/unverified guesses elsewhere in this module (trickplay, skip-intro).
 */
class EmbyConnectRemoteDataSource @Inject constructor(
    @EmbyOkHttpClient okHttpClient: OkHttpClient,
    @EmbyJson json: Json,
    // Injected purely to reuse authHeaders() for step 3 rather than rebuilding the
    // MediaBrowser-scheme string a second time - keeps the two call sites from silently drifting
    // apart over time. See exchangeToken's doc.
    private val remoteDataSource: EmbyRemoteDataSource,
) {
    private val api: EmbyConnectApi = Retrofit.Builder()
        .baseUrl(CONNECT_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(EmbyConnectApi::class.java)

    private val applicationHeader = mapOf("X-Application" to APPLICATION_HEADER_VALUE)

    suspend fun authenticate(email: String, password: String): EmbyConnectAuthResult =
        api.authenticate(applicationHeader, EmbyConnectAuthRequest(nameOrEmail = email, rawpw = password))

    suspend fun getServers(connectUserId: String, connectAccessToken: String): List<EmbyConnectServerDto> =
        api.getServers(
            headers = applicationHeader + ("X-Connect-UserToken" to connectAccessToken),
            userId = connectUserId,
        )

    /**
     * EmbyRemoteDataSource.authHeaders(accessKey) already produces exactly what this endpoint
     * needs: an X-Emby-Token equal to accessKey, plus an X-Emby-Authorization carrying the same
     * MediaBrowser-scheme client/device string with Token="{accessKey}" appended - so it's reused
     * as-is rather than duplicated here.
     */
    suspend fun exchangeToken(serverBaseUrl: String, accessKey: String, connectUserId: String): EmbyConnectExchangeResult =
        api.exchangeToken(
            url = (serverBaseUrl.trimEnd('/') + "/Connect/Exchange").toHttpUrl(),
            headers = remoteDataSource.authHeaders(accessKey),
            connectUserId = connectUserId,
        )
}
