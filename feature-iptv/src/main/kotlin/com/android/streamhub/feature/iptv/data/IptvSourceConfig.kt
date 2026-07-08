package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How the user's IPTV provider is reached - either a full Xtream Codes panel (structured
 * categories/streams/EPG) or a plain M3U playlist URL (flat channel list, no EPG API).
 * Persisted as JSON in DataStore, so field names are part of the on-disk format.
 */
@Serializable
sealed class IptvSourceConfig {
    @Serializable
    @SerialName("xtream")
    data class Xtream(
        val baseUrl: String,
        val username: String,
        val password: String,
    ) : IptvSourceConfig()

    @Serializable
    @SerialName("m3u")
    data class M3u(
        val playlistUrl: String,
        val epgUrl: String? = null,
    ) : IptvSourceConfig()
}

fun IptvSourceConfig.Xtream.liveStreamUrl(streamId: String, extension: String = "ts"): String =
    "${baseUrl.trimEnd('/')}/live/$username/$password/$streamId.$extension"

fun IptvSourceConfig.Xtream.vodStreamUrl(streamId: String, extension: String = "mp4"): String =
    "${baseUrl.trimEnd('/')}/movie/$username/$password/$streamId.$extension"
