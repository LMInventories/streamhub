package com.android.streamhub.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * screen (and eat the remote's input) with a keyboard. Merely hiding the keyboard again on focus
 * (an earlier attempt at this) raced against the field's own auto-show and lost unpredictably -
 * the initially-auto-focused field on a fresh screen behaved, but every field reached by
 * explicitly D-pad-navigating to it still popped the keyboard regardless.
 *
 * This is a structural fix instead of a race: pair with `readOnly = state.value` on the text
 * field itself. A read-only field can hold D-pad focus (so Up/Down navigation between fields
 * still works) but never starts a text input session, so there's nothing to auto-show a keyboard
 * for - it only becomes editable (and only then shows the keyboard) on an explicit D-pad
 * center/Enter "select" press, fully under the user's own control. Moving focus away resets it
 * back to read-only for next time.
 */
@Composable
fun rememberTvManualKeyboardReadOnly(): MutableState<Boolean> = remember { mutableStateOf(true) }

@Composable
fun Modifier.tvManualKeyboard(readOnly: MutableState<Boolean>): Modifier {
    val keyboardController = LocalSoftwareKeyboardController.current
    return this
        .onFocusChanged { state ->
            if (!state.isFocused) {
                readOnly.value = true
                keyboardController?.hide()
            }
        }
        .onPreviewKeyEvent { event ->
            if (readOnly.value &&
                event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                readOnly.value = false
                true
            } else {
                false
            }
        }
}
