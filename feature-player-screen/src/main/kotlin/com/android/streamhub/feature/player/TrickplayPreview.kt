package com.android.streamhub.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.streamhub.core.common.domain.TrickplayInfo
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.player.trickplayCoordinateFor
import com.android.streamhub.core.player.trickplayTileUrl

/**
 * A live scene preview above the seek bar while scrubbing, cropped from the server's own sprite
 * sheet rather than a dedicated thumbnail-per-position request - the crop is done with plain
 * Compose layout (a fixed-size clipped Box + an oversized AsyncImage offset so only the target
 * cell shows through) rather than manual bitmap manipulation, so repeated positions within the
 * same tile reuse Coil's ordinary image cache with no extra work here. contentScale is FillBounds
 * rather than the default Fit/Crop - the AsyncImage's requested size is already the sprite's true
 * pixel dimensions converted 1:1 to dp, so FillBounds at that exact size is a no-op scale, not a
 * distortion; Fit/Crop would instead letterbox/crop the *whole* sprite sheet, not just this cell.
 */
@Composable
fun TrickplayPreview(trickplay: TrickplayInfo, positionMs: Long, modifier: Modifier = Modifier) {
    val coordinate = remember(trickplay, positionMs) { trickplayCoordinateFor(positionMs, trickplay) }
    val density = LocalDensity.current
    val thumbWidth = with(density) { trickplay.width.toDp() }
    val thumbHeight = with(density) { trickplay.height.toDp() }

    Box(
        modifier = modifier
            .size(thumbWidth, thumbHeight)
            .clip(AppShapes.small)
            .background(Color.Black),
    ) {
        AsyncImage(
            model = trickplayTileUrl(trickplay, coordinate.tileIndex),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(thumbWidth * trickplay.tileGridColumns, thumbHeight * trickplay.tileGridRows)
                .offset(x = -thumbWidth * coordinate.column, y = -thumbHeight * coordinate.row),
        )
    }
}
