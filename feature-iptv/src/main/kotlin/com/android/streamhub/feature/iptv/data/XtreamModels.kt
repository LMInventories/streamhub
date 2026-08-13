package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamLiveCategory(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
)

@Serializable
data class XtreamLiveStream(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("stream_id")
    val streamId: String,
    @SerialName("name")
    val name: String,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    @SerialName("epg_channel_id")
    val epgChannelId: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String = "",
)

@Serializable
data class XtreamVodCategory(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
)

@Serializable
data class XtreamVodStream(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("stream_id")
    val streamId: String,
    @SerialName("name")
    val name: String,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    // Falls back to "mp4" when a provider omits this - the same default the reference IPTV
    // clients (StreamVault-IPTV, another-iptv-player) use for Xtream VOD playback URLs.
    @SerialName("container_extension")
    val containerExtension: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String = "",
    // Unix-epoch-seconds string, per common Xtream panel convention - unverified against a live
    // panel (see IptvVodRepository.getRecentMovies's own doc). Non-nullable with an empty-string
    // default (same pattern as categoryId above) rather than a nullable field, since
    // FlexibleStringSerializer is a non-null KSerializer<String> - toLongOrNull() downstream
    // already treats "" the same as "no value" either way.
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("added")
    val added: String = "",
)
