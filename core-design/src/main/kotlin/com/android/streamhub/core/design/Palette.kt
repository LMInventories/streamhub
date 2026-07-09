package com.android.streamhub.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class ThemeMode { DARK, LIGHT }

/**
 * Raw brand color values, shared by both the phone (material3) and TV (tv-material) themes so
 * the two never drift from each other. The surface/text tokens are backed by mutableStateOf and
 * swapped wholesale by [applyMode] - every screen that reads e.g. Palette.Background inside a
 * Composable already participates in Compose's snapshot system just by reading it, so applying a
 * mode change here recomposes the whole app without any of those ~100+ call sites needing to
 * change to read from a CompositionLocal instead. Accent/error/source-badge colors are brand
 * colors, not surface tokens, so they stay fixed across both modes.
 */
object Palette {
    var Background by mutableStateOf(Dark.background)
        private set
    var Surface by mutableStateOf(Dark.surface)
        private set
    var SurfaceElevated by mutableStateOf(Dark.surfaceElevated)
        private set
    var Border by mutableStateOf(Dark.border)
        private set
    var TextPrimary by mutableStateOf(Dark.textPrimary)
        private set
    var TextMuted by mutableStateOf(Dark.textMuted)
        private set

    var mode: ThemeMode = ThemeMode.DARK
        private set

    fun applyMode(mode: ThemeMode) {
        this.mode = mode
        val tokens = if (mode == ThemeMode.LIGHT) Light else Dark
        Background = tokens.background
        Surface = tokens.surface
        SurfaceElevated = tokens.surfaceElevated
        Border = tokens.border
        TextPrimary = tokens.textPrimary
        TextMuted = tokens.textMuted
    }

    // Deliberate one-off light exception (not a light-theme variant of the tokens above) for
    // long-press context menus specifically - off-white surface with dark text so the menu reads
    // as a distinct floating control rather than just another dark panel on the dark app chrome.
    // Fixed regardless of app theme mode, same reasoning as the brand colors below.
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

    private object Dark {
        val background = Color(0xFF0F1115)
        val surface = Color(0xFF1A1D23)
        val surfaceElevated = Color(0xFF242832)
        val border = Color(0xFF2A2E37)
        val textPrimary = Color(0xFFF2F3F5)
        val textMuted = Color(0xFF9CA3AF)
    }

    private object Light {
        val background = Color(0xFFF7F6F3)
        val surface = Color(0xFFFFFFFF)
        val surfaceElevated = Color(0xFFEFEDE8)
        val border = Color(0xFFDDDAD2)
        val textPrimary = Color(0xFF1C1B1A)
        val textMuted = Color(0xFF6B6862)
    }
}
