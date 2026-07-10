package com.android.streamhub.core.design

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * `Modifier.clickable`/`combinedClickable` already make an element D-pad focusable for free (they
 * chain `Modifier.focusable()` internally) - what they *don't* give is any visible "this is what's
 * focused right now" cue, since their default indication is a touch ripple and a D-pad has no
 * finger to press. Custom clickable rows/chips across this app (the EPG grid's channel rows,
 * category filter chips, ...) need this wired in explicitly - unlike tv-material3's own Card/
 * Button, which already draw a focus border/scale out of the box. Without it, D-pad navigation
 * technically works but looks and feels completely broken, since there's no cursor to follow.
 *
 * Pass the same [interactionSource] given to the element's own clickable modifier so this reads
 * the same focus state clickable is already tracking, rather than a second, separate one.
 */
@Composable
fun Modifier.tvFocusBorder(interactionSource: InteractionSource, shape: Shape = RectangleShape): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    return if (isFocused) this.border(width = 2.dp, color = Palette.Accent, shape = shape) else this
}
