package com.android.streamhub.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one signature visual motif: a segmented, tuning-meter-style progress indicator
 * used everywhere something's "how far along" - live channel elapsed time, EPG program
 * progress, buffering - instead of three different plain bars. Read-only; the interactive
 * player scrubber uses a recolored Slider instead (drag/keyboard/accessibility handling that's
 * risky to reimplement blind without a device to verify on), but shares this same accent
 * language.
 */
@Composable
fun SignalBar(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = Palette.Accent,
    inactiveColor: Color = Palette.Border,
    segmentCount: Int = 28,
    height: Dp = 4.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val filledSegments = (clamped * segmentCount).toInt()

    Canvas(modifier = modifier.height(height)) {
        val gapPx = 2.dp.toPx()
        val segmentWidthPx = (size.width - gapPx * (segmentCount - 1)) / segmentCount
        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)

        for (index in 0 until segmentCount) {
            val x = index * (segmentWidthPx + gapPx)
            drawRoundRect(
                color = if (index < filledSegments) activeColor else inactiveColor,
                topLeft = Offset(x, 0f),
                size = Size(segmentWidthPx, size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}
