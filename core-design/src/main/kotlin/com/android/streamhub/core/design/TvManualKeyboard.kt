package com.android.streamhub.core.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * A text field normally pops the software keyboard the instant it gains focus - correct for
 * touch, where focus only happens because someone already tapped the field meaning to type, but
 * wrong for a D-pad: simply arrowing past a field on the way to another one shouldn't cover the
 * screen (and eat the remote's input) with a keyboard. This suppresses that automatic show and
 * instead brings the keyboard up only on an explicit "select" press (D-pad center/Enter) once the
 * field already has focus - arrow to the field you want, then press center to actually type in it.
 */
@Composable
fun Modifier.tvManualKeyboard(): Modifier {
    val keyboardController = LocalSoftwareKeyboardController.current
    return this
        .onFocusChanged { state -> if (state.isFocused) keyboardController?.hide() }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                keyboardController?.show()
            }
            false
        }
}
