package com.android.streamhub.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette

data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * One shared hub for every source, reachable as its own bottom-nav/tab-row tab now rather than
 * only via a settings gear icon elsewhere - each source gets its own card-style section here so
 * it reads as a distinct area, ready for more than just a sign-in link once per-source settings
 * (beyond just connecting) actually exist. Live TV/VOD both read the same Xtream/M3U config, so
 * they share a single section rather than two - Emby is listed but disabled until that
 * integration lands (Milestone 4).
 */
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    onIptvClick: () -> Unit,
    onJellyfinClick: () -> Unit,
) {
    val entries = listOf(
        SettingsEntry(
            title = "Live TV & VOD",
            subtitle = "Xtream Codes or M3U playlist sign-in",
            enabled = true,
            onClick = onIptvClick,
        ),
        SettingsEntry(
            title = "Jellyfin",
            subtitle = "Server sign-in",
            enabled = true,
            onClick = onJellyfinClick,
        ),
        SettingsEntry(
            title = "Emby",
            subtitle = "Not set up yet",
            enabled = false,
            onClick = {},
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .padding(paddingValues)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(AppShapes.medium)
                    .background(Palette.Surface)
                    .clickable(enabled = entry.enabled, onClick = entry.onClick)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                BasicText(
                    text = entry.title,
                    style = TextStyle(
                        color = if (entry.enabled) Palette.TextPrimary else Palette.TextMuted,
                        fontSize = 17.sp,
                    ),
                )
                BasicText(
                    text = entry.subtitle,
                    style = TextStyle(color = Palette.TextMuted, fontSize = 13.sp),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
