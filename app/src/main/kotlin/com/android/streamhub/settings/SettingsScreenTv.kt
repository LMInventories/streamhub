package com.android.streamhub.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsRow
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsRowDivider
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsSection
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsSectionTab

private val SECTION_LIST_WIDTH = 260.dp

/**
 * Master-detail layout: a left-hand list of section names (App, Live TV & VOD, Emby, Jellyfin,
 * ...) and a right-hand pane showing only the selected section's rows. Replaces the previous
 * single long scrolling column of stacked section cards - on a TV, reaching e.g. "Jellyfin" meant
 * scrolling past every other section first, which is more effortful with a D-pad than a thumb.
 * This mirrors how Android TV's own system Settings and tvOS Settings are structured. Same
 * section/row data as phone (buildSettingsSections) so the two form factors can never drift on
 * what actually exists - only the layout differs.
 */
@Composable
fun SettingsScreenTv(
    onIptvClick: () -> Unit,
    onEmbyClick: () -> Unit,
    onEmbyPlaybackClick: () -> Unit,
    onEmbyLibrariesClick: () -> Unit,
    onEmbyHomeOrderClick: () -> Unit,
    onJellyfinClick: () -> Unit,
    onJellyfinPlaybackClick: () -> Unit,
    onJellyfinLibrariesClick: () -> Unit,
    onJellyfinHomeOrderClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onIptvPlaybackClick: () -> Unit,
    onScheduledManagementClick: () -> Unit,
    onDownloadsManagementClick: () -> Unit,
    // Back anywhere in this screen (a row, or a section tab - no distinction) always jumps D-pad
    // focus straight to the nav rail, full stop. Simplified down from an earlier "row -> section
    // tab -> rail" ladder that kept reportedly failing specifically for the section-tab case -
    // this is the direct, no-nuance behavior instead: one action, no branching on what's focused.
    onRequestRailFocus: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val updateSubtitle = rememberUpdateCheckSubtitle(settingsViewModel)
    val onCheckForUpdateClick = rememberUpdateRowClick(settingsViewModel)

    val sections = buildSettingsSections(
        onIptvClick = onIptvClick,
        onEmbyClick = onEmbyClick,
        onEmbyPlaybackClick = onEmbyPlaybackClick,
        onEmbyLibrariesClick = onEmbyLibrariesClick,
        onEmbyHomeOrderClick = onEmbyHomeOrderClick,
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

    var selectedSectionIndex by remember {
        mutableIntStateOf(settingsViewModel.lastFocusedSectionIndex.coerceIn(sections.indices))
    }

    // Requesters keyed by section title / row label (stable strings) rather than derived from
    // `sections` itself - buildSettingsSections returns fresh onClick lambdas every recomposition,
    // so keying `remember` off that list would reallocate a new FocusRequester every frame. The
    // section/row shape is fixed for this screen's lifetime, so a one-time `remember` is safe.
    val sectionTabFocusRequesters = remember { sections.map { FocusRequester() } }
    val rowFocusRequesters = remember {
        sections.associate { section -> section.title to section.rows.associate { it.label to FocusRequester() } }
    }

    // Restores focus to wherever the user drilled in from (see SettingsViewModel.setLastFocused)
    // instead of always resetting to the very top - falls back to the first section tab on a
    // genuinely fresh entry, when nothing has been focused here yet.
    LaunchedEffect(Unit) {
        val targetRequester = settingsViewModel.lastFocusedRowLabel
            ?.let { label -> rowFocusRequesters[sections.getOrNull(selectedSectionIndex)?.title]?.get(label) }
            ?: sectionTabFocusRequesters.getOrNull(selectedSectionIndex)
        runCatching { targetRequester?.requestFocus() }
    }

    // Both a raw key interceptor AND a BackHandler, redundantly, doing the exact same thing -
    // three earlier attempts (two BackHandler variants, then a raw-key-only variant) were each
    // individually well-supported by how Android's input pipeline is documented to work, and each
    // still reportedly failed to catch Back with a section tab focused. Since I can't reproduce or
    // instrument this without the actual device, mounting both mechanisms means whichever one
    // Android actually routes the physical Back press through on this hardware, something catches
    // it - if the raw key path consumes the event first (the normal case), BackHandler's
    // dispatcher-based fallback never even fires, so there's no double-handling risk either way.
    BackHandler { onRequestRailFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back || event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                onRequestRailFocus()
                true
            },
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(SECTION_LIST_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                sections.forEachIndexed { index, section ->
                    TvSettingsSectionTab(
                        title = section.title,
                        selected = index == selectedSectionIndex,
                        modifier = Modifier.focusRequester(sectionTabFocusRequesters[index]),
                        onClick = { selectedSectionIndex = index },
                    )
                }
            }

            val selectedSection = sections.getOrNull(selectedSectionIndex)
            if (selectedSection != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 32.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    TvSettingsSection(title = selectedSection.title) {
                        selectedSection.rows.forEachIndexed { index, row ->
                            TvSettingsRow(
                                label = row.label,
                                subtitle = row.subtitle,
                                icon = row.icon,
                                enabled = row.enabled,
                                modifier = Modifier.focusRequester(
                                    rowFocusRequesters.getValue(selectedSection.title).getValue(row.label),
                                ),
                                onClick = {
                                    settingsViewModel.setLastFocused(selectedSectionIndex, row.label)
                                    row.onClick()
                                },
                            )
                            if (index != selectedSection.rows.lastIndex) TvSettingsRowDivider()
                        }
                    }
                }
            }
        }
    }
}
