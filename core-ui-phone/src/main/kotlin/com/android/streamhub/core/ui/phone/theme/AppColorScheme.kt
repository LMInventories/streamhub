package com.android.streamhub.core.ui.phone.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.ThemeMode

/**
 * Every phone-reachable screen that wraps itself in its own local MaterialTheme (rather than
 * relying on an ambient one it can't count on - see each call site's own comment) builds its
 * ColorScheme through this one function instead of a private module-level darkColorScheme(...)
 * val, so all of them pick up theme-mode changes together. Reads Palette's mutableStateOf-backed
 * tokens fresh on every call, so a Composable calling this recomposes automatically when the
 * user's theme preference changes - no manual wiring needed at any of those call sites.
 */
@Composable
fun appColorScheme(): ColorScheme = if (Palette.mode == ThemeMode.LIGHT) {
    lightColorScheme(
        primary = Palette.Accent,
        onPrimary = Palette.TextPrimary,
        secondary = Palette.AccentMuted,
        onSecondary = Palette.TextPrimary,
        background = Palette.Background,
        onBackground = Palette.TextPrimary,
        surface = Palette.Surface,
        onSurface = Palette.TextPrimary,
        surfaceVariant = Palette.SurfaceElevated,
        onSurfaceVariant = Palette.TextMuted,
        outline = Palette.Border,
        error = Palette.Error,
    )
} else {
    darkColorScheme(
        primary = Palette.Accent,
        onPrimary = Palette.TextPrimary,
        secondary = Palette.AccentMuted,
        onSecondary = Palette.TextPrimary,
        background = Palette.Background,
        onBackground = Palette.TextPrimary,
        surface = Palette.Surface,
        onSurface = Palette.TextPrimary,
        surfaceVariant = Palette.SurfaceElevated,
        onSurfaceVariant = Palette.TextMuted,
        outline = Palette.Border,
        error = Palette.Error,
    )
}
