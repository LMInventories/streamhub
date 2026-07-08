package com.android.streamhub.nav

import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryType

// Shared between PhoneNavHost and TvNavHost rather than duplicated in each.

fun jellyfinItemTypeFor(library: JellyfinLibraryInfo): JellyfinItemType = when (library.type) {
    JellyfinLibraryType.MOVIES -> JellyfinItemType.MOVIE
    JellyfinLibraryType.TV_SHOWS -> JellyfinItemType.SERIES
}

/** A series row/tile (Continue Watching, Next Up, and a TV Shows library's grid can all surface one) opens the season/episode picker; everything else (movies, episodes) opens the play/resume detail screen directly. */
fun jellyfinDetailRouteFor(item: JellyfinItemInfo): String = when (item.type) {
    JellyfinItemType.SERIES -> Route.jellyfinSeriesDetailRoute(item.id)
    else -> Route.jellyfinItemDetailRoute(item.id)
}
