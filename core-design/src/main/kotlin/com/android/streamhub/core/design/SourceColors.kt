package com.android.streamhub.core.design

import androidx.compose.ui.graphics.Color
import com.android.streamhub.core.common.domain.SourceType

/** The one place that maps a backend to its badge color - every screen should go through this rather than hardcoding hues. */
fun SourceType.badgeColor(): Color = when (this) {
    SourceType.IPTV -> Palette.SourceIptv
    SourceType.JELLYFIN -> Palette.SourceJellyfin
    SourceType.EMBY -> Palette.SourceEmby
    SourceType.RECORDING -> Palette.SourceRecording
    SourceType.MOCK -> Palette.TextMuted
}

fun SourceType.label(): String = when (this) {
    SourceType.IPTV -> "IPTV"
    SourceType.JELLYFIN -> "Jellyfin"
    SourceType.EMBY -> "Emby"
    SourceType.RECORDING -> "Recording"
    SourceType.MOCK -> "Demo"
}
