package com.android.streamhub.settings

import com.android.streamhub.core.design.ThemeMode
import kotlinx.serialization.Serializable

enum class TextScale(val multiplier: Float, val label: String) {
    SMALL(0.9f, "Small"),
    DEFAULT(1f, "Default"),
    LARGE(1.15f, "Large"),
    EXTRA_LARGE(1.3f, "Extra large"),
}

@Serializable
data class AppUiSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val textScale: TextScale = TextScale.DEFAULT,
)
