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

    data object JellyfinSettings : Route()

    data class VodItemDetail(val itemId: String) : Route()

    data class VodSeriesDetail(val seriesId: String) : Route()

    data class JellyfinLibrary(val libraryId: String, val itemType: String) : Route()

    data object JellyfinFavorites : Route()

    data class JellyfinItemDetail(val itemId: String) : Route()

    data class JellyfinSeriesDetail(val seriesId: String) : Route()

    data object Recordings : Route()

    data class Player(val itemId: String, val sourceType: SourceType) : Route()

    companion object {
        const val HOME_PATTERN = "home"
        const val LIVE_TV_PATTERN = "live_tv"
        const val VOD_PATTERN = "vod"
        const val EMBY_HOME_PATTERN = "emby_home"
        const val JELLYFIN_HOME_PATTERN = "jellyfin_home"
        const val SETTINGS_PATTERN = "settings"
        const val IPTV_SETTINGS_PATTERN = "iptv_settings"
        const val JELLYFIN_SETTINGS_PATTERN = "jellyfin_settings"
        const val JELLYFIN_PLAYBACK_SETTINGS_PATTERN = "jellyfin_playback_settings"
        const val JELLYFIN_LIBRARY_VISIBILITY_PATTERN = "jellyfin_library_visibility"
        const val JELLYFIN_HOME_SECTION_ORDER_PATTERN = "jellyfin_home_section_order"
        const val VOD_ITEM_DETAIL_PATTERN = "vod_item_detail/{itemId}"
        const val VOD_SERIES_DETAIL_PATTERN = "vod_series_detail/{seriesId}"
        const val JELLYFIN_LIBRARY_PATTERN = "jellyfin_library/{libraryId}/{itemType}"
        const val JELLYFIN_FAVORITES_PATTERN = "jellyfin_favorites"
        const val JELLYFIN_ITEM_DETAIL_PATTERN = "jellyfin_item_detail/{itemId}"
        const val JELLYFIN_SERIES_DETAIL_PATTERN = "jellyfin_series_detail/{seriesId}"
        const val RECORDINGS_PATTERN = "recordings"
        const val PLAYER_PATTERN = "player/{sourceType}/{itemId}"

        fun playerRoute(itemId: String, sourceType: SourceType): String =
            "player/${sourceType.name}/$itemId"

        // itemId can be "vod:<id>" or "episode:<seriesId>:<episodeId>" - colons are safe as a
        // raw path segment (only "/" is a route separator), so no encoding needed, same as the
        // existing Player route already relies on for VOD ids.
        fun vodItemDetailRoute(itemId: String): String = "vod_item_detail/$itemId"

        fun vodSeriesDetailRoute(seriesId: String): String = "vod_series_detail/$seriesId"

        // itemType is a plain enum name (e.g. "MOVIE"/"SERIES") - no special characters, safe
        // as a raw path segment. The library's display name isn't passed here - the screen looks
        // it up itself, same reasoning as vodItemDetailRoute not carrying a title either.
        fun jellyfinLibraryRoute(libraryId: String, itemType: String): String = "jellyfin_library/$libraryId/$itemType"

        fun jellyfinItemDetailRoute(itemId: String): String = "jellyfin_item_detail/$itemId"

        fun jellyfinSeriesDetailRoute(seriesId: String): String = "jellyfin_series_detail/$seriesId"
    }
}
