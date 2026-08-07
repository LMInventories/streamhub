package com.android.streamhub.feature.emby.data

import kotlinx.serialization.Serializable

/**
 * Stores the *result* of a successful sign-in (server address, resolved user id, access token) -
 * not the raw username/password. Every subsequent API call authenticates via this token (Emby's
 * own convention, same shared .NET-lineage design as Jellyfin's, which Emby predates), so there's
 * no reason to keep the password around once sign-in has actually succeeded.
 */
@Serializable
data class EmbySourceConfig(
    val serverUrl: String,
    val username: String,
    val userId: String,
    val accessToken: String,
    // Defaulted, not required - a config saved before this field existed still deserializes
    // (kotlinx.serialization needs a default for a field missing from old stored JSON), and it
    // just falls back to serverUrl wherever it's displayed.
    val serverName: String? = null,
)
