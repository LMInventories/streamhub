package com.android.streamhub.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Plain TextStyles rather than a androidx.compose.material3.Typography (or tv-material3
 * equivalent) - core-design stays theme-library-agnostic so both PhoneTheme and TvTheme can
 * build their own Typography object from the same slot values without this module depending on
 * either material3 artifact.
 */
object AppTextStyles {
    val displayLarge = style(AppFonts.Display, FontWeight.Bold, 45.sp, (-0.5).sp)
    val displayMedium = style(AppFonts.Display, FontWeight.Bold, 36.sp, (-0.25).sp)
    val displaySmall = style(AppFonts.Display, FontWeight.Bold, 32.sp)

    val headlineLarge = style(AppFonts.Display, FontWeight.Bold, 28.sp)
    val headlineMedium = style(AppFonts.Display, FontWeight.Medium, 24.sp)
    val headlineSmall = style(AppFonts.Display, FontWeight.Medium, 20.sp)

    val titleLarge = style(AppFonts.Display, FontWeight.Medium, 20.sp)
    val titleMedium = style(AppFonts.Body, FontWeight.SemiBold, 16.sp, 0.1.sp)
    val titleSmall = style(AppFonts.Body, FontWeight.SemiBold, 14.sp, 0.1.sp)

    val bodyLarge = style(AppFonts.Body, FontWeight.Normal, 16.sp, 0.15.sp)
    val bodyMedium = style(AppFonts.Body, FontWeight.Normal, 14.sp, 0.2.sp)
    val bodySmall = style(AppFonts.Body, FontWeight.Normal, 12.sp, 0.2.sp)

    val labelLarge = style(AppFonts.Body, FontWeight.Medium, 14.sp, 0.1.sp)
    val labelMedium = style(AppFonts.Body, FontWeight.Medium, 12.sp, 0.3.sp)
    val labelSmall = style(AppFonts.Body, FontWeight.Medium, 11.sp, 0.3.sp)

    /** For timestamps/durations/EPG times - a small "console" touch, used sparingly. */
    val mono = style(AppFonts.Mono, FontWeight.Medium, 13.sp)

    private fun style(
        family: androidx.compose.ui.text.font.FontFamily,
        weight: FontWeight,
        size: TextUnit,
        letterSpacing: TextUnit = TextUnit.Unspecified,
    ) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, letterSpacing = letterSpacing)
}
