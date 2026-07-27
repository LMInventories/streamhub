package com.android.streamhub.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette as PaletteIcon
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.BuildConfig
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.update.UpdateCheckResult
import com.android.streamhub.update.rememberUpdateInstallAction

data class SettingsRow(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

data class SettingsSection(
    val title: String,
    val rows: List<SettingsRow>,
)

/**
 * Shared by both SettingsScreen (phone) and SettingsScreenTv so the two form factors' section/row
 * definitions can never drift apart from each other - only how they're laid out differs.
 */
fun buildSettingsSections(
    onIptvClick: () -> Unit,
    onJellyfinClick: () -> Unit,
    onJellyfinPlaybackClick: () -> Unit,
    onJellyfinLibrariesClick: () -> Unit,
    onJellyfinHomeOrderClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onIptvPlaybackClick: () -> Unit,
    onScheduledManagementClick: () -> Unit,
    onDownloadsManagementClick: () -> Unit,
    updateCheckSubtitle: String,
    onCheckForUpdateClick: () -> Unit,
): List<SettingsSection> = listOf(
    SettingsSection(
        title = "App",
        rows = listOf(
            SettingsRow(label = "Appearance", subtitle = "Theme, text size", icon = Icons.Filled.PaletteIcon, onClick = onAppearanceClick),
            SettingsRow(label = "Downloads", subtitle = "Manage offline downloads and storage", icon = Icons.Filled.Download, onClick = onDownloadsManagementClick),
            SettingsRow(label = "Check for Updates", subtitle = updateCheckSubtitle, icon = Icons.Filled.SystemUpdate, onClick = onCheckForUpdateClick),
        ),
    ),
    SettingsSection(
        title = "Live TV & VOD",
        rows = listOf(
            SettingsRow(label = "Source", subtitle = "Xtream Codes or M3U playlist sign-in", icon = Icons.Filled.Cloud, onClick = onIptvClick),
            SettingsRow(
                label = "Playback",
                subtitle = "Resume channel, sort order, EPG format, preview size",
                icon = Icons.Filled.PlayCircle,
                onClick = onIptvPlaybackClick,
            ),
            SettingsRow(
                label = "Scheduled",
                subtitle = "Manage reminders and pending recordings",
                icon = Icons.Filled.Schedule,
                onClick = onScheduledManagementClick,
            ),
        ),
    ),
    SettingsSection(
        title = "Jellyfin",
        rows = listOf(
            SettingsRow(label = "Source", subtitle = "Server sign-in", icon = Icons.Filled.Cloud, onClick = onJellyfinClick),
            SettingsRow(
                label = "Playback",
                subtitle = "Preferred audio/subtitle language, max bitrate",
                icon = Icons.Filled.PlayCircle,
                onClick = onJellyfinPlaybackClick,
            ),
            SettingsRow(
                label = "Libraries",
                subtitle = "Choose which libraries show up in the app",
                icon = Icons.Filled.VideoLibrary,
                onClick = onJellyfinLibrariesClick,
            ),
            SettingsRow(
                label = "Home screen order",
                subtitle = "Reorder Continue Watching, Next Up, and the rest",
                icon = Icons.Filled.Reorder,
                onClick = onJellyfinHomeOrderClick,
            ),
        ),
    ),
    SettingsSection(
        title = "Emby",
        rows = listOf(
            SettingsRow(label = "Source", subtitle = "Not set up yet", icon = Icons.Filled.Cloud, enabled = false, onClick = {}),
        ),
    ),
)

/** Shared by SettingsScreen/SettingsScreenTv - derives the "Check for Updates" row's subtitle from SettingsViewModel's reactive state so both form factors read identically. */
@Composable
fun rememberUpdateCheckSubtitle(viewModel: SettingsViewModel): String {
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingForUpdate.collectAsStateWithLifecycle()
    val lastCheckResult by viewModel.lastCheckResult.collectAsStateWithLifecycle()
    return when {
        isChecking -> "Checking..."
        updateInfo != null -> "Update available: v${updateInfo!!.versionName}"
        lastCheckResult == UpdateCheckResult.UP_TO_DATE -> "Up to date - version ${BuildConfig.VERSION_NAME}"
        lastCheckResult == UpdateCheckResult.CHECK_FAILED -> "Check failed - version ${BuildConfig.VERSION_NAME}"
        else -> "Version ${BuildConfig.VERSION_NAME}"
    }
}

/** Shared by SettingsScreen/SettingsScreenTv - if an update's already known, tapping the row installs it; otherwise it triggers a fresh manual check. */
@Composable
fun rememberUpdateRowClick(viewModel: SettingsViewModel): () -> Unit {
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val startUpdate = rememberUpdateInstallAction(
        canInstallPackages = viewModel::canInstallPackages,
        installPermissionIntent = viewModel::requestInstallPermissionIntent,
        onStartDownload = viewModel::startUpdateDownload,
    )
    return {
        if (updateInfo != null) startUpdate() else viewModel.checkForUpdatesNow()
    }
}

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
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val updateSubtitle = rememberUpdateCheckSubtitle(settingsViewModel)
    val onCheckForUpdateClick = rememberUpdateRowClick(settingsViewModel)

    val sections = buildSettingsSections(
        onIptvClick = onIptvClick,
        onJellyfinClick = onJellyfinClick,
        onJellyfinPlaybackClick = onJellyfinPlaybackClick,
        onJellyfinLibrariesClick = onJellyfinLibrariesClick,
        onJellyfinHomeOrderClick = onJellyfinHomeOrderClick,
        onAppearanceClick = onAppearanceClick,
        onIptvPlaybackClick = onIptvPlaybackClick,
        onScheduledManagementClick = onScheduledManagementClick,
        onDownloadsManagementClick = onDownloadsManagementClick,
        updateCheckSubtitle = updateSubtitle,
        onCheckForUpdateClick = onCheckForUpdateClick,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .padding(paddingValues)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Settings",
            color = Palette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        // One flat, bordered card per section (not a box-within-a-box per row) - every section
        // reads as a single uniform surface with divider-separated rows, the same "grouped list"
        // convention every platform settings app uses, rather than each row being its own
        // separately-rounded pill stacked inside the section.
        sections.forEach { section ->
            Text(
                text = section.title,
                color = Palette.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clip(AppShapes.large)
                    .background(Palette.Surface)
                    .border(1.dp, Palette.Border, AppShapes.large),
            ) {
                section.rows.forEachIndexed { index, row ->
                    SettingsRowItem(row)
                    if (index != section.rows.lastIndex) {
                        HorizontalDivider(color = Palette.Border, modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRowItem(row: SettingsRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.enabled, onClick = row.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            row.icon,
            contentDescription = null,
            tint = if (row.enabled) Palette.Accent else Palette.TextMuted,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(text = row.label, color = if (row.enabled) Palette.TextPrimary else Palette.TextMuted, fontSize = 15.sp)
            Text(
                text = row.subtitle,
                color = Palette.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (row.enabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Palette.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
