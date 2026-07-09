package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.Serializable

enum class ChannelSortOrder { PLAYLIST, ALPHABETICAL }

enum class PreviewPlayerSize(val multiplier: Float, val label: String) {
    SMALL(0.75f, "Small"),
    MEDIUM(1f, "Medium"),
    LARGE(1.3f, "Large"),
}

@Serializable
data class IptvAppSettings(
    val resumeLastChannel: Boolean = true,
    val channelSortOrder: ChannelSortOrder = ChannelSortOrder.PLAYLIST,
    val use24HourTime: Boolean = true,
    val previewPlayerSize: PreviewPlayerSize = PreviewPlayerSize.MEDIUM,
)
