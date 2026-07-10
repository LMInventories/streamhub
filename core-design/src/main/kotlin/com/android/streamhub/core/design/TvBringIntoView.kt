package com.android.streamhub.core.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch

/**
 * Settings forms scroll a plain `Modifier.verticalScroll` Column, which normally auto-scrolls a
 * focused child into view - but that relies on the focus/scroll relocation chain reaching all the
 * way up cleanly, which breaks in practice once a nested horizontally-scrolling row (the
 * auto-update mode/day/hour filter chips) sits between the focused item and the outer scroll
 * state. A D-pad user moving focus past the visible area then sees nothing move - the screen
 * looks stuck rather than just needing a scroll. This makes the behavior explicit and reliable
 * instead of depending on that implicit chain: request this element be scrolled into view the
 * moment it gains focus, regardless of how deep it's nested.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvScrollsIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state -> if (state.isFocused) scope.launch { requester.bringIntoView() } }
}
