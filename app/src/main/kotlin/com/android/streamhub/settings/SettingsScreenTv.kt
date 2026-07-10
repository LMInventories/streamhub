package com.android.streamhub.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvScrollsIntoViewOnFocus

/**
 * TV gets its own layout rather than reusing SettingsScreen's phone one - a single narrow column
 * of stacked cards left most of a TV's width empty and gave every clickable row zero D-pad focus
 * feedback (tv-material3's own Card already draws a focus border/scale for free, unlike the plain
 * Modifier.clickable rows the phone version uses). Same section/row data as phone (buildSettingsSections)
 * so the two can never drift on what settings actually exist - only the layout differs: one
 * horizontally-scrolling shelf of cards per section, echoing HomeScreenTv's own dashboard-card
 * look rather than inventing a third visual language for this TV app.
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
) {
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
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

        sections.forEach { section ->
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Accent,
                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                section.rows.forEach { row -> SettingsRowCard(row) }
            }
        }
    }
}

@Composable
private fun SettingsRowCard(row: SettingsRow) {
    Card(
        onClick = if (row.enabled) row.onClick else ({}),
        modifier = Modifier
            .width(240.dp)
            .alpha(if (row.enabled) 1f else 0.5f)
            .tvScrollsIntoViewOnFocus(),
    ) {
        Column(
            modifier = Modifier
                .background(Palette.Accent.copy(alpha = 0.16f), AppShapes.large)
                .padding(16.dp),
        ) {
            Icon(row.icon, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(28.dp))
            Text(text = row.label, modifier = Modifier.padding(top = 12.dp))
            Text(text = row.subtitle, color = Palette.TextMuted, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
