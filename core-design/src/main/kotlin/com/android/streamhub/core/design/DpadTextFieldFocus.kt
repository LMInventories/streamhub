package com.android.streamhub.core.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * A focused single-line BasicTextField (what Material3's OutlinedTextField/TextField are built
 * on) still treats DPAD Up/Down as cursor-movement keys and consumes them even though there's no
 * second line to move a cursor to - on a touchscreen this is invisible (nobody presses an arrow
 * key), but on a TV remote it reads as focus getting permanently stuck the moment a field is
 * entered, since Up/Down never reach Compose's normal 2D focus search to move to the next field.
 *
 * onPreviewKeyEvent modifiers run top-down (parent to child) before a focused node's own key
 * handling gets the event, so placing this directly on a text field's own Modifier intercepts
 * Up/Down before the field's internal cursor-movement logic can swallow them, and redirects them
 * to the previous/next focusable instead - the same thing Left/Right already do by default
 * between chips/buttons elsewhere in these forms. Safe to use on phone too: none of the fields
 * this is applied to are multi-line, so there's no real cursor-movement behavior being overridden.
 */
fun Modifier.dpadMovesFocusVertically(focusManager: FocusManager): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
        else -> false
    }
}
