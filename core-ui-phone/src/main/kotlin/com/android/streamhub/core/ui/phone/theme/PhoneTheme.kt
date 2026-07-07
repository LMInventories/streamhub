package com.android.streamhub.core.ui.phone.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.AppTextStyles
import com.android.streamhub.core.design.Palette

// Dark-first by design, not "supports dark mode" - there's no light variant, matching the TV
// theme's approach. A toggle can be added later if it turns out to be wanted, but the visual
// identity is built on the dark palette specifically.
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
    outline = Palette.Border,
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

private val AppShapesM3 = Shapes(
    extraSmall = AppShapes.extraSmall,
    small = AppShapes.small,
    medium = AppShapes.medium,
    large = AppShapes.large,
    extraLarge = AppShapes.extraLarge,
)

@Composable
fun StreamHubPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapesM3,
        content = content,
    )
}
