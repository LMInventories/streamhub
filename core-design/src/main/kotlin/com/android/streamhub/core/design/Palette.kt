package com.android.streamhub.core.design

import androidx.compose.ui.graphics.Color

/**
 * Raw brand color values, shared by both the phone (material3) and TV (tv-material) themes so
 * the two never drift from each other. Everything below is dark-first by design, not just
 * "supports dark mode" - there is no light variant.
 */
object Palette {
    val Background = Color(0xFF0F1115)
    val Surface = Color(0xFF1A1D23)
    val SurfaceElevated = Color(0xFF242832)
    val Border = Color(0xFF2A2E37)

    val TextPrimary = Color(0xFFF2F3F5)
    val TextMuted = Color(0xFF9CA3AF)

    // Deliberate one-off light exception (not a light-theme variant of the tokens above) for
    // long-press context menus specifically - off-white surface with dark text so the menu reads
    // as a distinct floating control rather than just another dark panel on the dark app chrome.
    val ContextMenuSurface = Color(0xFFF5F2EC)
    val ContextMenuText = Color(0xFF1C1B1A)

    /** Primary accent - deliberately outside the IPTV/Jellyfin/Emby badge hues below, so it never reads as "which source". */
    val Accent = Color(0xFF7C5CFC)
    val AccentMuted = Color(0xFF5B44B8)

    val Error = Color(0xFFEF4444)

    // Source badges - Jellyfin/Emby reuse each project's own brand color as a deliberate nod;
    // IPTV gets a warm "live broadcast" amber since it has no single upstream brand to match.
    val SourceIptv = Color(0xFFFF9F1C)
    val SourceJellyfin = Color(0xFF00A4DC)
    val SourceEmby = Color(0xFF52B54B)
    // Distinct from the favourite heart's pink (0xFFE0245E, used inline where that appears) and
    // from Error above - a classic "record button" red, universally recognizable for its own
    // meaning rather than reusing either of those other reds' semantics.
    val SourceRecording = Color(0xFFDC2626)
}
