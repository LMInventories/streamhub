package com.android.streamhub.core.design

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared "two-or-more mutually exclusive filter" control - first built for VOD's Movies/TV Shows
 * toggle, extracted here once Jellyfin Favourites needed the exact same look rather than a second
 * near-identical private copy. BasicText, not material3/tv-material3's Text - see SourceBadge's
 * own comment on why core-design stays theme-library-agnostic.
 */
@Composable
fun PillToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Optional - lets a TV settings screen whose first control is a PillToggle (rather than a
    // TvSettingsRow/TvChoiceChip) attach the same kind of initial-focus requester those use. Left
    // null everywhere else (VOD/Favourites toggles, phone), so this has zero effect on any
    // existing call site that doesn't pass it.
    firstOptionFocusRequester: FocusRequester? = null,
) {
    Row(modifier = modifier.background(Palette.Surface, AppShapes.pill).padding(4.dp)) {
        options.forEachIndexed { index, label ->
            PillToggleOption(
                label = label,
                selected = index == selectedIndex,
                modifier = if (index == 0 && firstOptionFocusRequester != null) Modifier.focusRequester(firstOptionFocusRequester) else Modifier,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun PillToggleOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // This had no D-pad focus indicator at all before tvFocusBorder was added here - only the
    // already-selected option was ever visually distinguishable, so arrowing onto a *different*,
    // not-yet-selected option showed nothing telling you the cursor had actually moved there.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(if (selected) Palette.Accent else Color.Transparent)
            .tvFocusBorder(interactionSource, AppShapes.pill)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BasicText(
            text = label,
            style = AppTextStyles.labelLarge.copy(color = if (selected) Color.White else Palette.TextMuted),
        )
    }
}
