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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.design.tvManualKeyboard
import com.android.streamhub.core.design.tvScrollsIntoViewOnFocus
import com.android.streamhub.core.ui.phone.theme.appColorScheme

/**
 * Shared TV-native building blocks for the Settings hub and every screen it opens - built once so
 * all of them look and focus-behave consistently rather than each reinventing this. Mirrors
 * SettingsScreen.kt's (phone) grouped-card visual structure, sized up for TV, using
 * Modifier.tvFocusBorder for D-pad focus feedback - the established idiom for list-style TV rows
 * (already proven in RecordingTile/TvScaffold's own nav rail/EPG channel rows), since tv-material3's
 * own Card/Button focus styling (scale/glow) is built for grid-shaped content, not list rows.
 */
@Composable
fun TvSettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(text = title, style = TvMaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp))
    }
}

/** A grouped, rounded-card section of rows - same Palette/AppShapes tokens phone's SettingsScreen already uses. */
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
                .border(1.dp, Palette.Border, AppShapes.large),
        ) {
            content()
        }
    }
}

/** One row inside a TvSettingsSection - list-style (not a card grid), so D-pad focus is drawn via tvFocusBorder rather than relying on tv-material3 Card's own grid-shaped focus scale/glow. */
@Composable
fun TvSettingsRow(
    label: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Palette.Accent else Palette.TextMuted,
                modifier = Modifier.size(26.dp),
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

/** A non-clickable divider matching TvSettingsSection's row spacing - use between TvSettingsRows in the same section. */
@Composable
fun TvSettingsRowDivider() {
    HorizontalDivider(color = Palette.Border, modifier = Modifier.padding(start = 20.dp))
}

/** A TvSettingsRow variant for a boolean setting - a checkmark instead of a chevron, no tv-material3 Switch dependency needed. */
@Composable
fun TvSettingsToggleRow(label: String, subtitle: String? = null, checked: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 18.dp),
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

/** A single selectable pill - the TV-native equivalent of Material3's FilterChip (which needs an M3 theme this screen doesn't otherwise want), used for mutually-exclusive option rows (Auto-update mode, Cache Duration, ...). */
@Composable
fun TvChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(if (selected) Palette.Accent else Palette.Surface)
            .border(1.dp, if (selected) Palette.Accent else Palette.Border, AppShapes.pill)
            .tvFocusBorder(interactionSource, AppShapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = label, color = if (selected) Color.White else Palette.TextPrimary)
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

/** The outer padding a TV settings screen's root Column should use around every TvSettingsSection. */
val TvSettingsContentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp)
