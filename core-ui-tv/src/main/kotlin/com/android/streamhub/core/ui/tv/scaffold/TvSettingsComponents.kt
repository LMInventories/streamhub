package com.android.streamhub.core.ui.tv.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.dpadMovesFocusVertically
import com.android.streamhub.core.design.rememberTvManualKeyboardReadOnly
import com.android.streamhub.core.design.tvManualKeyboard
import com.android.streamhub.core.design.tvScrollsIntoViewOnFocus
import com.android.streamhub.core.design.tvSettingsFocusIndicator
import com.android.streamhub.core.ui.phone.theme.appColorScheme

/**
 * Shared TV-native building blocks for the Settings hub and every screen it opens - built once so
 * all of them look and focus-behave consistently rather than each reinventing this. Mirrors
 * SettingsScreen.kt's (phone) grouped-card visual structure, sized up for TV, using
 * Modifier.tvSettingsFocusIndicator for D-pad focus feedback - the established idiom for
 * list-style TV rows (also used by TvScaffold's own nav rail, so the two share one visual
 * language), since tv-material3's own Card/Button focus styling (scale/glow) is built for
 * grid-shaped content, not list rows.
 */

/**
 * Requests D-pad focus onto whatever the caller attaches the returned [FocusRequester] to -
 * without this, Compose's default focus target on entering a TV settings screen is the nav rail
 * (or nothing at all), not the screen's own content. Every settings sub-screen needs this exact
 * idiom; centralized here after an audit found the Settings hub had it hand-rolled but every
 * screen the hub opens didn't, which is exactly the kind of "D-pad lands somewhere wrong on
 * entry" bug that reads as "navigation feels broken."
 *
 * [readyKey] defaults to firing once on first composition, which is right for screens whose first
 * control exists immediately. Screens that gate their real content behind a loading state (the
 * first row doesn't exist yet on first composition) should pass that loading flag instead, so the
 * request re-fires once it flips to loaded - otherwise the one-shot Unit version would silently
 * no-op (requestFocus on a not-yet-attached FocusRequester fails safely, but never retries).
 */
@Composable
fun rememberTvSettingsInitialFocus(readyKey: Any? = Unit): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(readyKey) { runCatching { focusRequester.requestFocus() } }
    return focusRequester
}

@Composable
fun TvSettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Palette.TextPrimary)
        }
        Text(
            text = title,
            color = Palette.TextPrimary,
            style = TvMaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * A grouped, rounded-card section of rows - same Palette/AppShapes tokens phone's SettingsScreen
 * already uses. A faint top-edge highlight (a lighter 1px line just inside the border) gives the
 * card a subtle "raised panel" read rather than a flat outlined rectangle.
 */
@Composable
fun TvSettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        Text(
            text = title,
            color = Palette.TextMuted,
            style = TvMaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .background(Palette.Surface)
                .border(1.dp, Palette.Border, AppShapes.large)
                .drawBehind {
                    drawLine(
                        color = Palette.TextPrimary.copy(alpha = 0.05f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            content()
        }
    }
}

/** One row inside a TvSettingsSection - list-style (not a card grid), so D-pad focus is drawn via tvSettingsFocusIndicator rather than relying on tv-material3 Card's own grid-shaped focus scale/glow. */
@Composable
fun TvSettingsRow(
    label: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Inset before tvSettingsFocusIndicator so the focus highlight is a rounded pill
            // floating inside the section card rather than a square block butting against its
            // 20dp rounded border. The inset is subtracted from the inner padding below, so row
            // metrics are unchanged.
            .padding(horizontal = SettingsRowFocusInset, vertical = 4.dp)
            .tvSettingsFocusIndicator(interactionSource, shape = AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp - SettingsRowFocusInset, vertical = 16.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Palette.Accent else Palette.TextMuted,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = if (icon != null) 18.dp else 0.dp)) {
            Text(text = label, color = if (enabled) Palette.TextPrimary else Palette.TextMuted)
            if (subtitle != null) {
                Text(text = subtitle, color = Palette.TextMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (enabled && showChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted)
        }
    }
}

/**
 * How far a row's focus highlight is inset from the section card's own edge. Kept as a named
 * constant because the row's inner padding subtracts it to hold the overall row metrics steady,
 * and the divider indents past it so it lines up with the row text rather than the highlight.
 */
private val SettingsRowFocusInset = 8.dp

/** A non-clickable divider matching TvSettingsSection's row spacing - use between TvSettingsRows in the same section. */
@Composable
fun TvSettingsRowDivider() {
    HorizontalDivider(color = Palette.Border, modifier = Modifier.padding(start = 20.dp))
}

/** A TvSettingsRow variant for a boolean setting - a checkmark instead of a chevron, no tv-material3 Switch dependency needed. */
@Composable
fun TvSettingsToggleRow(label: String, subtitle: String? = null, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Same rounded-highlight inset as TvSettingsRow - see its comment.
            .padding(horizontal = SettingsRowFocusInset, vertical = 4.dp)
            .tvSettingsFocusIndicator(interactionSource, shape = AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(horizontal = 20.dp - SettingsRowFocusInset, vertical = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Palette.TextPrimary)
            if (subtitle != null) {
                Text(text = subtitle, color = Palette.TextMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (checked) {
            Icon(Icons.Filled.Check, contentDescription = "Enabled", tint = Palette.Accent)
        }
    }
}

/**
 * A single selectable pill - the TV-native equivalent of Material3's FilterChip (which needs an
 * M3 theme this screen doesn't otherwise want), used for mutually-exclusive option rows
 * (Auto-update mode, Cache Duration, ...). Selected reads via an accent border + the leading-edge
 * bar tvSettingsFocusIndicator already draws for `selected`, rather than a solid accent fill -
 * keeps it visually consistent with TvSettingsRow/the nav rail instead of being the one component
 * that looks like a different, heavier control.
 */
@Composable
fun TvChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(Palette.Surface)
            .border(1.dp, if (selected) Palette.Accent else Palette.Border, AppShapes.pill)
            .tvSettingsFocusIndicator(interactionSource, selected = selected, shape = AppShapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = label, color = Palette.TextPrimary)
    }
}

/**
 * Wraps Material3's OutlinedTextField (tv-material3 has no text field of its own) plus the same
 * D-pad "hold focus without popping the keyboard until Enter/DirectionCenter is pressed" behavior
 * every other text-entry screen in this app already needs (see TvManualKeyboard.kt) - bundles the
 * whole modifier chain (tvManualKeyboard + tvScrollsIntoViewOnFocus + dpadMovesFocusVertically)
 * that was previously copy-pasted per field. Locally wraps a plain Material3 MaterialTheme, same
 * pattern already used for M3-dependent dialogs inside otherwise tv-material3 screens (see
 * PlayerScreenTv's own dialogs) - nesting M3's MaterialTheme doesn't affect tv-material3
 * components elsewhere in the tree, they read a completely separate ambient.
 */
@Composable
fun TvSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val focusManager = LocalFocusManager.current
    val readOnly = rememberTvManualKeyboardReadOnly()
    MaterialTheme(colorScheme = appColorScheme()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { M3Text(label) },
            enabled = enabled,
            readOnly = readOnly.value,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = modifier
                .fillMaxWidth()
                .dpadMovesFocusVertically(focusManager)
                .tvScrollsIntoViewOnFocus()
                .tvManualKeyboard(readOnly),
        )
    }
}

/**
 * One row in the Settings hub's left-hand section-jump list (App, Live TV & VOD, Emby, Jellyfin,
 * ...) - the master half of its master-detail layout. Styled with the same
 * tvSettingsFocusIndicator `selected` treatment as the nav rail, so "which section am I in"
 * (persistent) reads consistently with "which tab am I on" one level up, and "currently focused"
 * layers on top the same way it does everywhere else in this screen.
 */
@Composable
fun TvSettingsSectionTab(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsRowFocusInset, vertical = 3.dp)
            .tvSettingsFocusIndicator(interactionSource, selected = selected, shape = AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp - SettingsRowFocusInset, vertical = 14.dp),
    ) {
        Text(
            text = title,
            color = if (selected) Palette.TextPrimary else Palette.TextMuted,
            style = TvMaterialTheme.typography.titleSmall,
        )
    }
}

/** The outer padding a TV settings screen's root Column should use around every TvSettingsSection. */
val TvSettingsContentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp)
