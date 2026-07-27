package com.android.streamhub.settings

import com.android.streamhub.core.design.ThemeMode
import kotlinx.serialization.Serializable

enum class TextScale(val multiplier: Float, val label: String) {
    SMALL(0.9f, "Small"),
    DEFAULT(1f, "Default"),
    LARGE(1.15f, "Large"),
    EXTRA_LARGE(1.3f, "Extra large"),
}

// Which screen the app opens directly into on a genuine cold start (process launched fresh) -
// never applied when merely resuming from background, see AppLaunchState/AppLaunchRedirectViewModel.
enum class AppLaunchDestination(val label: String) {
    LIVE_TV("Live TV"),
    VOD("VOD"),
    JELLYFIN("Jellyfin"),
    EMBY("Emby"),
}

@Serializable
data class AppUiSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val textScale: TextScale = TextScale.DEFAULT,
    val launchDestination: AppLaunchDestination = AppLaunchDestination.LIVE_TV,
)
