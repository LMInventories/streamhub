package com.android.streamhub.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.streamhub.core.design.Palette

data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * One shared hub for every source's sign-in config, reachable from Home. Live TV/VOD both read
 * the same Xtream/M3U config, so they share a single entry here rather than two - Jellyfin/Emby
 * are listed but disabled until those integrations actually land (Milestone 3/4).
 */
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    onIptvClick: () -> Unit,
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
            subtitle = "Not set up yet",
            enabled = false,
            onClick = {},
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
            .padding(vertical = 8.dp),
    ) {
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
