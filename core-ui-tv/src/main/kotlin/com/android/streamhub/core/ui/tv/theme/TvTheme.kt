package com.android.streamhub.core.ui.tv.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.AppTextStyles
import com.android.streamhub.core.design.Palette

// TV screens are always viewed from a distance in a dark room, so - same as phone - there's no
// light variant, just the one dark identity shared via core-design's Palette.
private val AppColorScheme = darkColorScheme(
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

private val AppTypography = Typography(
    displayLarge = AppTextStyles.displayLarge,
    displayMedium = AppTextStyles.displayMedium,
    displaySmall = AppTextStyles.displaySmall,
    headlineLarge = AppTextStyles.headlineLarge,
    headlineMedium = AppTextStyles.headlineMedium,
    headlineSmall = AppTextStyles.headlineSmall,
    titleLarge = AppTextStyles.titleLarge,
    titleMedium = AppTextStyles.titleMedium,
    titleSmall = AppTextStyles.titleSmall,
    bodyLarge = AppTextStyles.bodyLarge,
    bodyMedium = AppTextStyles.bodyMedium,
    bodySmall = AppTextStyles.bodySmall,
    labelLarge = AppTextStyles.labelLarge,
    labelMedium = AppTextStyles.labelMedium,
    labelSmall = AppTextStyles.labelSmall,
)

private val AppShapesTv = Shapes(
    extraSmall = AppShapes.extraSmall,
    small = AppShapes.small,
    medium = AppShapes.medium,
    large = AppShapes.large,
    extraLarge = AppShapes.extraLarge,
)

@Composable
fun StreamHubTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapesTv,
        content = content,
    )
}
