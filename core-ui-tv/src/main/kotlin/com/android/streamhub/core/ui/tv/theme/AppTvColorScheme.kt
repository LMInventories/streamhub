package com.android.streamhub.core.ui.tv.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.ThemeMode

/** tv-material3 equivalent of core-ui-phone's appColorScheme() - same Palette tokens, different ColorScheme type, so it can't be the same function. */
@Composable
fun appTvColorScheme(): ColorScheme = if (Palette.mode == ThemeMode.LIGHT) {
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
        border = Palette.Border,
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
        border = Palette.Border,
        error = Palette.Error,
    )
}
