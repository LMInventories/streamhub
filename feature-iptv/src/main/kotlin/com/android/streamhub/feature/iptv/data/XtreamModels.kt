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
