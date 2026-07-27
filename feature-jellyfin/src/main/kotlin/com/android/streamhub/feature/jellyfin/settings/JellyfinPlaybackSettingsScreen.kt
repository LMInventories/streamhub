package com.android.streamhub.feature.jellyfin.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsRow
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsRowDivider
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsSection
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTopBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText

// ISO 639-2 codes - the fixed subset Jellyfin libraries most commonly carry, not an exhaustive
// list. "No preference" (null) always leaves the player to fall back on its own default
// selection, same as never having set anything here.
private val LANGUAGE_OPTIONS = listOf(
    null to "No preference",
    "eng" to "English",
    "spa" to "Spanish",
    "fre" to "French",
    "ger" to "German",
    "ita" to "Italian",
    "por" to "Portuguese",
    "rus" to "Russian",
    "jpn" to "Japanese",
    "kor" to "Korean",
    "chi" to "Chinese",
    "ara" to "Arabic",
    "hin" to "Hindi",
)

private val BITRATE_OPTIONS = listOf(
    null to "Unlimited (direct play)",
    40 to "40 Mbps",
    20 to "20 Mbps",
    10 to "10 Mbps",
    5 to "5 Mbps",
    2 to "2 Mbps",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinPlaybackSettingsScreen(
    onDone: () -> Unit,
    viewModel: JellyfinPlaybackSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Playback") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsDropdownRow(
                    label = "Preferred audio language",
                    options = LANGUAGE_OPTIONS,
                    selected = uiState.preferredAudioLanguage,
                    onSelect = viewModel::setPreferredAudioLanguage,
                )
                SettingsDropdownRow(
                    label = "Preferred subtitle language",
                    options = LANGUAGE_OPTIONS,
                    selected = uiState.preferredSubtitleLanguage,
                    onSelect = viewModel::setPreferredSubtitleLanguage,
                )
                SettingsDropdownRow(
                    label = "Max streaming bitrate",
                    options = BITRATE_OPTIONS,
                    selected = uiState.maxStreamingBitrateMbps,
                    onSelect = viewModel::setMaxStreamingBitrateMbps,
                )
                Text(
                    text = "Applies the next time something starts playing - already-playing streams aren't interrupted.",
                    color = Palette.TextMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun <T> SettingsDropdownRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: options.first().second

    val interactionSource = remember { MutableInteractionSource() }
    // Closing the DropdownMenu (select or dismiss) disposes whichever item currently holds D-pad
    // focus with nothing left to take over, so without this TV's focus system falls back to its
    // own default target (the nav rail) instead of landing back on the row that opened the menu.
    val anchorFocusRequester = remember { FocusRequester() }
    val closeMenu: () -> Unit = { expanded = false; runCatching { anchorFocusRequester.requestFocus() } }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Palette.TextMuted, fontSize = 13.sp)
        Box(modifier = Modifier.padding(top = 4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.small)
                    .background(Palette.Surface)
                    .tvFocusBorder(interactionSource, AppShapes.small)
                    .focusRequester(anchorFocusRequester)
                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { expanded = true }
                    .padding(16.dp),
            ) {
                Text(text = selectedLabel, color = Palette.TextPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
                // A DropdownMenu never moves D-pad focus into itself on TV - without this, the
                // remote's presses kept hitting whatever was focused underneath, not this menu.
                val firstItemFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
                options.forEachIndexed { index, (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                        onClick = { onSelect(value); closeMenu() },
                    )
                }
            }
        }
    }
}

/** TV-native sibling of JellyfinPlaybackSettingsScreen - same JellyfinPlaybackSettingsViewModel. A Material3 DropdownMenu doesn't fit a tv-material3 screen well (D-pad focus behaves oddly inside it, see the phone version's own comment on that), so this uses a full-screen Dialog picker instead. */
@Composable
fun JellyfinPlaybackSettingsScreenTv(
    onDone: () -> Unit,
    viewModel: JellyfinPlaybackSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TvSettingsTopBar(title = "Playback", onBack = onDone)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp)) {
            TvSettingsSection(title = "Preferences") {
                TvSettingsPickerRow(
                    label = "Preferred audio language",
                    options = LANGUAGE_OPTIONS,
                    selected = uiState.preferredAudioLanguage,
                    onSelect = viewModel::setPreferredAudioLanguage,
                )
                TvSettingsRowDivider()
                TvSettingsPickerRow(
                    label = "Preferred subtitle language",
                    options = LANGUAGE_OPTIONS,
                    selected = uiState.preferredSubtitleLanguage,
                    onSelect = viewModel::setPreferredSubtitleLanguage,
                )
                TvSettingsRowDivider()
                TvSettingsPickerRow(
                    label = "Max streaming bitrate",
                    options = BITRATE_OPTIONS,
                    selected = uiState.maxStreamingBitrateMbps,
                    onSelect = viewModel::setMaxStreamingBitrateMbps,
                )
            }
            TvText(
                text = "Applies the next time something starts playing - already-playing streams aren't interrupted.",
                color = Palette.TextMuted,
            )
        }
    }
}

@Composable
private fun <T> TvSettingsPickerRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: options.first().second

    TvSettingsRow(label = label, subtitle = selectedLabel, onClick = { expanded = true })

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.large)
                    .background(Palette.Surface)
                    .padding(vertical = 8.dp),
            ) {
                LazyColumn(modifier = Modifier.fillMaxHeight(fraction = 0.6f)) {
                    items(options) { (value, optionLabel) ->
                        TvSettingsRow(
                            label = optionLabel,
                            showChevron = false,
                            onClick = { onSelect(value); expanded = false },
                        )
                    }
                }
            }
        }
    }
}
