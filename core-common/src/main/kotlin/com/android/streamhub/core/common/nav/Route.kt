package com.android.streamhub.core.common.nav

import com.android.streamhub.core.common.domain.SourceType

/**
 * Shared nav destinations for both the phone and TV UI trees, so Master Search (and anything
 * else added later) can deep-link into the player without depending on either UI module.
 */
sealed class Route {
    data object Home : Route()

    data object LiveTv : Route()

    data object Vod : Route()

    data object EmbyHome : Route()

    data object JellyfinHome : Route()

    data object Settings : Route()

    data object IptvSettings : Route()

    data class Player(val itemId: String, val sourceType: SourceType) : Route()

    companion object {
        const val HOME_PATTERN = "home"
        const val LIVE_TV_PATTERN = "live_tv"
        const val VOD_PATTERN = "vod"
        const val EMBY_HOME_PATTERN = "emby_home"
        const val JELLYFIN_HOME_PATTERN = "jellyfin_home"
        const val SETTINGS_PATTERN = "settings"
        const val IPTV_SETTINGS_PATTERN = "iptv_settings"
        const val PLAYER_PATTERN = "player/{sourceType}/{itemId}"

        fun playerRoute(itemId: String, sourceType: SourceType): String =
            "player/${sourceType.name}/$itemId"
    }
}
