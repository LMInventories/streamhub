package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.Serializable

enum class ChannelSortOrder { PLAYLIST, ALPHABETICAL }

// Multipliers relative to the current ("Medium") preview size - Small is exactly 50% smaller,
// Large exactly 50% larger. Controlled from Settings > Appearance (not a Live TV-specific
// setting), even though the underlying value stays here for IptvBrowseRepository/LiveTvViewModel
// to keep reading directly - :app can depend on :feature-iptv, not the other way around.
enum class PreviewPlayerSize(val multiplier: Float, val label: String) {
    SMALL(0.5f, "Small"),
    MEDIUM(1f, "Medium"),
    LARGE(1.5f, "Large"),
}

// How long the channel list (IptvBrowseRepository) and EPG guide (EpgGridRepository) stay cached
// before a genuine re-fetch - user-tunable "faster loads" (longer) vs "fresher data" (shorter)
// trade-off. 1 day matches EpgGridRepository's old hardcoded ~20h default most closely.
val CACHE_DURATION_DAY_OPTIONS = listOf(1, 3, 5, 7)

@Serializable
data class IptvAppSettings(
    val resumeLastChannel: Boolean = true,
    val channelSortOrder: ChannelSortOrder = ChannelSortOrder.PLAYLIST,
    val use24HourTime: Boolean = true,
    val previewPlayerSize: PreviewPlayerSize = PreviewPlayerSize.MEDIUM,
    val cacheDurationDays: Int = 1,
)
