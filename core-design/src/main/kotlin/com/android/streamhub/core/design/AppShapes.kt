package com.android.streamhub.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** A bit rounder than Material3's defaults (12dp cards) - matches the friendlier, card-forward reference look. */
object AppShapes {
    val extraSmall = RoundedCornerShape(8.dp)
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
}
