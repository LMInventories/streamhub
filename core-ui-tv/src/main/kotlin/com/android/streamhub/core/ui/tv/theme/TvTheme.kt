package com.android.streamhub.core.ui.tv.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.AppTextStyles

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
        colorScheme = appTvColorScheme(),
        typography = AppTypography,
        shapes = AppShapesTv,
        content = content,
    )
}
