package com.android.streamhub.core.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * All three files are variable fonts (one file, a weight axis) - referencing the same resource
 * at several FontWeight values lets the system renderer interpolate the right instance rather
 * than needing separate static files per weight.
 */
object AppFonts {
    val Display = FontFamily(
        Font(R.font.space_grotesk, FontWeight.Normal),
        Font(R.font.space_grotesk, FontWeight.Medium),
        Font(R.font.space_grotesk, FontWeight.Bold),
    )

    val Body = FontFamily(
        Font(R.font.inter, FontWeight.Normal),
        Font(R.font.inter, FontWeight.Medium),
        Font(R.font.inter, FontWeight.SemiBold),
        Font(R.font.inter, FontWeight.Bold),
    )

    val Mono = FontFamily(
        Font(R.font.jetbrains_mono, FontWeight.Normal),
        Font(R.font.jetbrains_mono, FontWeight.Medium),
    )
}
