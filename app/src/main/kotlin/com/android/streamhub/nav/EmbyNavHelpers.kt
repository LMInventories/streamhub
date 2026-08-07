package com.android.streamhub.nav

import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType
import com.android.streamhub.feature.emby.data.EmbyLibraryInfo
import com.android.streamhub.feature.emby.data.EmbyLibraryType

// Shared between PhoneNavHost and TvNavHost rather than duplicated in each - mirrors JellyfinNavHelpers.kt.

fun embyItemTypeFor(library: EmbyLibraryInfo): EmbyItemType = when (library.type) {
    EmbyLibraryType.MOVIES -> EmbyItemType.MOVIE
    EmbyLibraryType.TV_SHOWS -> EmbyItemType.SERIES
}

/** A series row/tile opens the season/episode picker; everything else (movies, episodes) opens the play/resume detail screen directly. */
fun embyDetailRouteFor(item: EmbyItemInfo): String = when (item.type) {
    EmbyItemType.SERIES -> Route.embySeriesDetailRoute(item.id)
    else -> Route.embyItemDetailRoute(item.id)
}
