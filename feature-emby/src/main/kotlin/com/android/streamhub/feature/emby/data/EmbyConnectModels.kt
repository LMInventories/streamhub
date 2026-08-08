package com.android.streamhub.feature.emby.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Emby Connect (connect.emby.media) DTOs for the three-step sign-in flow documented on
// EmbyConnectRemoteDataSource: authenticate -> discover linked servers -> exchange for a
// server-local token. Field names/casing here are confirmed via two independent official sources
// (dev.emby.media and the MediaBrowser/Emby GitHub wiki agree) - unlike several other
// best-effort/unverified DTO guesses elsewhere in this module (trickplay, skip-intro), this is
// high confidence, not a hedge.

@Serializable
data class EmbyConnectAuthRequest(
    @SerialName("nameOrEmail") val nameOrEmail: String,
    @SerialName("rawpw") val rawpw: String,
)

@Serializable
data class EmbyConnectAuthResult(
    @SerialName("ConnectAccessToken") val connectAccessToken: String? = null,
    @SerialName("ConnectUserId") val connectUserId: String? = null,
)

/**
 * One server linked to a Connect account. [url] is the remote-access URL and [localAddress] the
 * local-network one - this app has no separate "remote vs local" concept elsewhere, so the local
 * address is preferred whenever it's present (faster/more reliable when reachable), falling back
 * to [url] otherwise. See EmbySettingsViewModel's finishConnectSignIn for that fallback.
 */
@Serializable
data class EmbyConnectServerDto(
    @SerialName("AccessKey") val accessKey: String? = null,
    @SerialName("SystemId") val systemId: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Url") val url: String? = null,
    @SerialName("LocalAddress") val localAddress: String? = null,
)

@Serializable
data class EmbyConnectExchangeResult(
    @SerialName("LocalUserId") val localUserId: String? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
)
