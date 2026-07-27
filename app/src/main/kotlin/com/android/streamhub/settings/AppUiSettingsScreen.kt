package com.android.streamhub.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.tv.material3.Text as TvText

private val THEME_OPTIONS = listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light")
private val TEXT_SCALE_OPTIONS = TextScale.entries.toList()

/** Same "wrap own MaterialTheme locally" reasoning as every other settings sub-screen - reachable from the TV nav host too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUiSettingsScreen(
    onDone: () -> Unit,
    viewModel: AppUiSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    Column(modifier = Modifier.fillMaxSize()) {
        TvSettingsTopBar(title = "Appearance", onBack = onDone)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp)) {
            TvSettingsSection(title = "Theme") {
                PillToggle(
                    options = THEME_OPTIONS.map { it.second },
                    selectedIndex = THEME_OPTIONS.indexOfFirst { it.first == uiState.themeMode }.coerceAtLeast(0),
                    onSelect = { index -> viewModel.setThemeMode(THEME_OPTIONS[index].first) },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
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

            TvText(text = "Applies across the whole app immediately.", color = Palette.TextMuted)
        }
    }
}
