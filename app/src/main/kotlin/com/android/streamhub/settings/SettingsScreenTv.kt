package com.android.streamhub.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsRow
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsRowDivider
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsSection

/**
 * Same section/row data as phone (buildSettingsSections) so the two form factors' settings can
 * never drift on what actually exists - only the layout differs. Rendered as a vertical grouped
 * list (TvSettingsSection/TvSettingsRow), matching phone's own SettingsScreen visual structure,
 * rather than the horizontally-scrolling card shelf this screen used before - list-shaped content
 * read as an awkward fit for a card-shelf idiom that works better for genuinely grid-shaped
 * content (Home's dashboard, poster rows).
 */
@Composable
fun SettingsScreenTv(
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        Column(modifier = Modifier.padding(top = 24.dp)) {
            sections.forEach { section ->
                TvSettingsSection(title = section.title) {
                    section.rows.forEachIndexed { index, row ->
                        TvSettingsRow(
                            label = row.label,
                            subtitle = row.subtitle,
                            icon = row.icon,
                            enabled = row.enabled,
                            onClick = row.onClick,
                        )
                        if (index != section.rows.lastIndex) TvSettingsRowDivider()
                    }
                }
            }
        }
    }
}
