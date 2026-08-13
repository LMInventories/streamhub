package com.android.streamhub.settings

import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    // Called when Back should escalate past this screen entirely (i.e. a section tab, not a row
    // in the detail pane, is focused) - maps to TvNavHost's shared tab-level Back ladder (previous
    // tab / open nav rail / exit confirmation).
    onBackFromTopLevel: () -> Unit,
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

    // Tracked the same focusGroup()+onFocusChanged idiom TvScaffold's nav rail uses for its own
    // "is focus anywhere in this subtree" signal.
    var detailPaneFocused by remember { mutableStateOf(false) }

    // Raw key interception (same mechanism LiveTvScreenTv's long-press-Back handling already
    // relies on) rather than androidx.activity.compose.BackHandler - a BackHandler-based version
    // of this exact "row vs section-tab" ladder was tried first and reported still exiting the app
    // with a section tab focused, which pointed at BackHandler's cross-composable dispatch-priority
    // behavior being less predictable here than assumed. onPreviewKeyEvent instead gives this
    // Column first, deterministic refusal of every Key.Back press for anything focused inside it -
    // no dependency on OnBackPressedDispatcher's registration order at all. Only KeyUp acts (KeyDown
    // is left unconsumed), matching this app's other raw Back handling.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back || event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                if (detailPaneFocused) {
                    runCatching { sectionTabFocusRequesters[selectedSectionIndex].requestFocus() }
                } else {
                    onBackFromTopLevel()
                }
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
                        .focusGroup()
                        .onFocusChanged { detailPaneFocused = it.hasFocus }
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
