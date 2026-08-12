package com.android.streamhub.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Focus/selected treatment for the TV nav rail and Settings screens specifically - a quieter
 * alternative to [tvFocusBorder]'s flat accent-fill-plus-thick-border box, which read as "messy
 * purple boxes" when every focusable on screen (and, on the nav rail, the selected-tab pill
 * underneath it too) used the exact same heavy treatment. Deliberately a separate function
 * rather than a new parameter on [tvFocusBorder] - that one is shared by 20+ other TV/phone-
 * landscape call sites outside this screen's scope, and this visual language is intentionally
 * specific to the rail/Settings list-row context.
 *
 * Focused uses [Palette.SurfaceElevated] as a fill (an existing "this is raised/active" token,
 * not an accent-color area fill) plus a slim outline and a rounded accent bar on the leading
 * edge, rather than accent color covering the whole shape - reads as elevation-plus-cursor
 * instead of a colored box. Selected-but-not-focused (e.g. the nav rail's current tab, a chosen
 * TvChoiceChip option in core-ui-tv) shows only the static leading-edge bar, so a
 * selected+focused element layers one consistent accent cue instead of two independently-styled
 * overlapping fills.
 */
@Composable
fun Modifier.tvSettingsFocusIndicator(
    interactionSource: InteractionSource,
    selected: Boolean = false,
    shape: Shape = AppShapes.small,
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val barAlpha by animateFloatAsState(if (isFocused || selected) 1f else 0f, label = "tvSettingsFocusBarAlpha")

    var result = this
    if (isFocused) {
        result = result
            .background(color = Palette.SurfaceElevated, shape = shape)
            .border(width = 1.5.dp, color = Palette.Accent, shape = shape)
    }
    if (barAlpha > 0f) {
        result = result.drawBehind {
            val barWidth = 4.dp.toPx()
            val barInset = 6.dp.toPx()
            drawRoundRect(
                color = Palette.Accent.copy(alpha = barAlpha),
                topLeft = Offset(0f, barInset),
                size = Size(barWidth, size.height - barInset * 2),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
    return result
}
