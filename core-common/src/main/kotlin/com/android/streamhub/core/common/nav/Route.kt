package com.android.streamhub.core.common.nav

import com.android.streamhub.core.common.domain.SourceType

/**
 * Shared nav destinations for both the phone and TV UI trees, so Master Search (and anything
 * else added later) can deep-link into the player without depending on either UI module.
 */
sealed class Route {
    data object Home : Route()

    data class Player(val itemId: String, val sourceType: SourceType) : Route()

    companion object {
        const val HOME_PATTERN = "home"
        const val PLAYER_PATTERN = "player/{sourceType}/{itemId}"

        fun playerRoute(itemId: String, sourceType: SourceType): String =
            "player/${sourceType.name}/$itemId"
    }
}
