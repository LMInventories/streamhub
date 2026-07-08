package com.android.streamhub.feature.jellyfin.data

import kotlinx.serialization.Serializable

/**
 * Stores the *result* of a successful sign-in (server address, resolved user id, access token) -
 * not the raw username/password. Every subsequent API call authenticates via this token
 * (Jellyfin's own convention, matching org.jellyfin.sdk.api.client.ApiClient.update), so there's
 * no reason to keep the password around once sign-in has actually succeeded.
 */
@Serializable
data class JellyfinSourceConfig(
    val serverUrl: String,
    val username: String,
    val userId: String,
    val accessToken: String,
)
