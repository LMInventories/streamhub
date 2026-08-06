package com.android.streamhub.core.player

import com.android.streamhub.core.common.domain.TrickplayInfo

/** Which sprite-sheet tile image [tileIndex] a thumbnail lives in, and its [column]/[row] within that tile's grid - see TrickplayInfo's own doc for the shape this is derived from. */
data class TrickplayCoordinate(val tileIndex: Int, val column: Int, val row: Int)

/**
 * Maps a playback position straight to which tile image to fetch and where within it to crop,
 * with no network round trip needed just to look that up - everything required is already in
 * [trickplay] (see TrickplayInfo's own doc for how the server lays these sprite sheets out).
 */
fun trickplayCoordinateFor(positionMs: Long, trickplay: TrickplayInfo): TrickplayCoordinate {
    val perTile = (trickplay.tileGridColumns * trickplay.tileGridRows).coerceAtLeast(1)
    val lastThumbnailIndex = (trickplay.thumbnailCount - 1).coerceAtLeast(0)
    val thumbnailIndex = (positionMs / trickplay.intervalMs.coerceAtLeast(1))
        .coerceIn(0L, lastThumbnailIndex.toLong())
        .toInt()
    val indexInTile = thumbnailIndex % perTile
    val columns = trickplay.tileGridColumns.coerceAtLeast(1)
    return TrickplayCoordinate(
        tileIndex = thumbnailIndex / perTile,
        column = indexInTile % columns,
        row = indexInTile / columns,
    )
}

/** Substitutes [TrickplayInfo.tileUrlTemplate]'s "{index}" placeholder with a concrete tile index - see that field's own doc for why the template exists instead of a plain URL. */
fun trickplayTileUrl(trickplay: TrickplayInfo, tileIndex: Int): String =
    trickplay.tileUrlTemplate.replace("{index}", tileIndex.toString())
