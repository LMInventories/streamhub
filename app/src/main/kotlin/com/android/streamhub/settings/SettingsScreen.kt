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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette

data class SettingsRow(
    val label: String,
    val subtitle: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

data class SettingsSection(
    val title: String,
    val rows: List<SettingsRow>,
)

/**
 * One shared hub for every source, reachable as its own bottom-nav/tab-row tab now rather than
 * only via settings gear icons scattered across other screens (all removed - this tab is the one
 * place to reach any of this now). Each source is its own card-style section with a non-clickable
 * title and one row per setting underneath - "Source" (sign-in/server/playlist config) is the
 * first row every section has, with room for more (Jellyfin's Playback/Libraries/Home screen
 * order rows are the first sections to actually use that room). Live TV/VOD both read the same
 * Xtream/M3U config, so they share a single section rather than two - Emby is listed but disabled
 * until that integration lands (Milestone 4).
 */
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    onIptvClick: () -> Unit,
    onJellyfinClick: () -> Unit,
    onJellyfinPlaybackClick: () -> Unit,
    onJellyfinLibrariesClick: () -> Unit,
    onJellyfinHomeOrderClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onIptvPlaybackClick: () -> Unit,
    onScheduledManagementClick: () -> Unit,
    onDownloadsManagementClick: () -> Unit,
) {
    val sections = listOf(
        SettingsSection(
            title = "App",
            rows = listOf(
                SettingsRow(label = "Appearance", subtitle = "Theme, text size", onClick = onAppearanceClick),
                SettingsRow(label = "Downloads", subtitle = "Manage offline downloads and storage", onClick = onDownloadsManagementClick),
            ),
        ),
        SettingsSection(
            title = "Live TV & VOD",
            rows = listOf(
                SettingsRow(label = "Source", subtitle = "Xtream Codes or M3U playlist sign-in", onClick = onIptvClick),
                SettingsRow(label = "Playback", subtitle = "Resume channel, sort order, EPG format, preview size", onClick = onIptvPlaybackClick),
                SettingsRow(label = "Scheduled", subtitle = "Manage reminders and pending recordings", onClick = onScheduledManagementClick),
            ),
        ),
        SettingsSection(
            title = "Jellyfin",
            rows = listOf(
                SettingsRow(label = "Source", subtitle = "Server sign-in", onClick = onJellyfinClick),
                SettingsRow(label = "Playback", subtitle = "Preferred audio/subtitle language, max bitrate", onClick = onJellyfinPlaybackClick),
                SettingsRow(label = "Libraries", subtitle = "Choose which libraries show up in the app", onClick = onJellyfinLibrariesClick),
                SettingsRow(label = "Home screen order", subtitle = "Reorder Continue Watching, Next Up, and the rest", onClick = onJellyfinHomeOrderClick),
            ),
        ),
        SettingsSection(
            title = "Emby",
            rows = listOf(
                SettingsRow(label = "Source", subtitle = "Not set up yet", enabled = false, onClick = {}),
            ),
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
        sections.forEach { section ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(AppShapes.medium)
                    .background(Palette.Surface)
                    .padding(16.dp),
            ) {
                BasicText(
                    text = section.title,
                    style = TextStyle(color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                section.rows.forEachIndexed { index, row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (index == 0) 10.dp else 8.dp)
                            .clip(AppShapes.small)
                            .background(Palette.SurfaceElevated)
                            .clickable(enabled = row.enabled, onClick = row.onClick)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        BasicText(
                            text = row.label,
                            style = TextStyle(
                                color = if (row.enabled) Palette.TextPrimary else Palette.TextMuted,
                                fontSize = 15.sp,
                            ),
                        )
                        BasicText(
                            text = row.subtitle,
                            style = TextStyle(color = Palette.TextMuted, fontSize = 13.sp),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
