package com.android.streamhub.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.PillToggle
import com.android.streamhub.core.design.ThemeMode
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsSection
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTopBar
import com.android.streamhub.core.ui.tv.scaffold.rememberTvSettingsInitialFocus
import com.android.streamhub.feature.iptv.data.PreviewPlayerSize
import androidx.tv.material3.Text as TvText

private val THEME_OPTIONS = listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light")
private val TEXT_SCALE_OPTIONS = TextScale.entries.toList()
private val LAUNCH_DESTINATION_OPTIONS = AppLaunchDestination.entries.toList()
private val PREVIEW_SIZE_OPTIONS = PreviewPlayerSize.entries.toList()
private val MATCH_REFRESH_RATE_OPTIONS = listOf(false to "Off", true to "On")

/** Same "wrap own MaterialTheme locally" reasoning as every other settings sub-screen - reachable from the TV nav host too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUiSettingsScreen(
    onDone: () -> Unit,
    viewModel: AppUiSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewPlayerSize by viewModel.previewPlayerSize.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", color = Palette.TextMuted)
                    PillToggle(
                        options = THEME_OPTIONS.map { it.second },
                        selectedIndex = THEME_OPTIONS.indexOfFirst { it.first == uiState.themeMode }.coerceAtLeast(0),
                        onSelect = { index -> viewModel.setThemeMode(THEME_OPTIONS[index].first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Text size", color = Palette.TextMuted)
                    PillToggle(
                        options = TEXT_SCALE_OPTIONS.map { it.label },
                        selectedIndex = TEXT_SCALE_OPTIONS.indexOf(uiState.textScale).coerceAtLeast(0),
                        onSelect = { index -> viewModel.setTextScale(TEXT_SCALE_OPTIONS[index]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("On app launch", color = Palette.TextMuted)
                    PillToggle(
                        options = LAUNCH_DESTINATION_OPTIONS.map { it.label },
                        selectedIndex = LAUNCH_DESTINATION_OPTIONS.indexOf(uiState.launchDestination).coerceAtLeast(0),
                        onSelect = { index -> viewModel.setLaunchDestination(LAUNCH_DESTINATION_OPTIONS[index]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Which screen the app opens into when launched fresh (not just resumed from background). Live TV also resumes whatever channel was last viewed.",
                        color = Palette.TextMuted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preview player size", color = Palette.TextMuted)
                    PillToggle(
                        options = PREVIEW_SIZE_OPTIONS.map { it.label },
                        selectedIndex = PREVIEW_SIZE_OPTIONS.indexOf(previewPlayerSize).coerceAtLeast(0),
                        onSelect = { index -> viewModel.setPreviewPlayerSize(PREVIEW_SIZE_OPTIONS[index]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Size of Live TV's mini-preview player above the channel/guide list.",
                        color = Palette.TextMuted,
                    )
                }

                Text(
                    text = "Applies across the whole app immediately.",
                    color = Palette.TextMuted,
                )
            }
        }
    }
}

/** TV-native sibling of AppUiSettingsScreen - same AppUiSettingsViewModel. PillToggle is theme-library-agnostic (BasicText, not Material3), so it's reused directly rather than restyled. */
@Composable
fun AppUiSettingsScreenTv(
    onDone: () -> Unit,
    viewModel: AppUiSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewPlayerSize by viewModel.previewPlayerSize.collectAsStateWithLifecycle()
    val firstOptionFocusRequester = rememberTvSettingsInitialFocus()

    Column(modifier = Modifier.fillMaxSize()) {
        TvSettingsTopBar(title = "Appearance", onBack = onDone)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 8.dp)) {
            TvSettingsSection(title = "Theme") {
                PillToggle(
                    options = THEME_OPTIONS.map { it.second },
                    selectedIndex = THEME_OPTIONS.indexOfFirst { it.first == uiState.themeMode }.coerceAtLeast(0),
                    onSelect = { index -> viewModel.setThemeMode(THEME_OPTIONS[index].first) },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    firstOptionFocusRequester = firstOptionFocusRequester,
                )
            }

            TvSettingsSection(title = "Text size") {
                PillToggle(
                    options = TEXT_SCALE_OPTIONS.map { it.label },
                    selectedIndex = TEXT_SCALE_OPTIONS.indexOf(uiState.textScale).coerceAtLeast(0),
                    onSelect = { index -> viewModel.setTextScale(TEXT_SCALE_OPTIONS[index]) },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }

            TvSettingsSection(title = "On app launch") {
                Column {
                    PillToggle(
                        options = LAUNCH_DESTINATION_OPTIONS.map { it.label },
                        selectedIndex = LAUNCH_DESTINATION_OPTIONS.indexOf(uiState.launchDestination).coerceAtLeast(0),
                        onSelect = { index -> viewModel.setLaunchDestination(LAUNCH_DESTINATION_OPTIONS[index]) },
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                    TvText(
                        text = "Which screen the app opens into when launched fresh (not just resumed from background). Live TV also resumes whatever channel was last viewed.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            TvSettingsSection(title = "Match content refresh rate") {
                Column {
                    PillToggle(
                        options = MATCH_REFRESH_RATE_OPTIONS.map { it.second },
                        selectedIndex = MATCH_REFRESH_RATE_OPTIONS.indexOfFirst { it.first == uiState.matchRefreshRate }.coerceAtLeast(0),
                        onSelect = { index -> viewModel.setMatchRefreshRate(MATCH_REFRESH_RATE_OPTIONS[index].first) },
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                    TvText(
                        text = "Switches the TV's output to match each video's frame rate during playback, to reduce judder. Restored to normal when playback stops. Some TVs/HDMI setups handle this better than others - try it and turn it back off if you see a flash or resolution banner you'd rather avoid.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            TvSettingsSection(title = "Preview player size") {
                Column {
                    PillToggle(
                        options = PREVIEW_SIZE_OPTIONS.map { it.label },
                        selectedIndex = PREVIEW_SIZE_OPTIONS.indexOf(previewPlayerSize).coerceAtLeast(0),
                        onSelect = { index -> viewModel.setPreviewPlayerSize(PREVIEW_SIZE_OPTIONS[index]) },
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                    TvText(
                        text = "Size of Live TV's mini-preview player above the channel/guide list.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            TvText(text = "Applies across the whole app immediately.", color = Palette.TextMuted)
        }
    }
}
