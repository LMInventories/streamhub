package com.android.streamhub.feature.iptv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.dpadMovesFocusVertically
import com.android.streamhub.core.design.rememberTvManualKeyboardReadOnly
import com.android.streamhub.core.design.tvManualKeyboard
import com.android.streamhub.core.design.tvScrollsIntoViewOnFocus
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.iptv.data.AUTO_UPDATE_DAY_OPTIONS
import com.android.streamhub.feature.iptv.data.AUTO_UPDATE_HOUR_OPTIONS
import com.android.streamhub.feature.iptv.data.AutoUpdateMode
import com.android.streamhub.feature.iptv.data.CACHE_DURATION_DAY_OPTIONS
import com.android.streamhub.core.ui.tv.scaffold.TvChoiceChip
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsSection
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTextField
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTopBar
import com.android.streamhub.core.ui.tv.scaffold.rememberTvSettingsInitialFocus
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Text as TvText

// This screen uses mobile Material3 components but is reachable from the TV nav host too, which
// only wraps content in tv-material3's MaterialTheme, not this one - without a local wrapper the
// text fields/buttons would fall back to M3's default light scheme when opened from TV. Same
// reasoning as EpgGridPanel/PlayerScreenTv's dialogs wrapping themselves rather than relying on
// an ambient theme they can't count on.

/**
 * Shared across phone and TV - it's a form, so there's no TV-specific idiom worth a second
 * implementation here (unlike the browse/player screens, which genuinely differ by form factor).
 * Every field/button/chip carries dpadMovesFocusVertically (Up/Down moves between fields instead
 * of getting eaten by BasicTextField's own cursor-movement key handling) and
 * tvScrollsIntoViewOnFocus (this form's nested horizontal chip rows otherwise break the implicit
 * focus-follows-scroll chain, leaving focus move past the visible area with nothing scrolling to
 * follow it) - see those functions' own comments for the full why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvSettingsScreen(
    onDone: () -> Unit,
    viewModel: IptvSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    MaterialTheme(colorScheme = appColorScheme()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("IPTV source") }, modifier = Modifier.statusBarsPadding())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.providerType == IptvProviderType.XTREAM,
                    onClick = { viewModel.selectProviderType(IptvProviderType.XTREAM) },
                    label = { Text("Xtream Codes") },
                    modifier = Modifier.tvScrollsIntoViewOnFocus(),
                )
                FilterChip(
                    selected = uiState.providerType == IptvProviderType.M3U,
                    onClick = { viewModel.selectProviderType(IptvProviderType.M3U) },
                    label = { Text("M3U playlist") },
                    modifier = Modifier.tvScrollsIntoViewOnFocus(),
                )
            }

            if (uiState.providerType == IptvProviderType.XTREAM) {
                val baseUrlReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.xtreamBaseUrl,
                    onValueChange = viewModel::updateXtreamBaseUrl,
                    label = { Text("Server URL (e.g. http://host:port)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    readOnly = baseUrlReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(baseUrlReadOnly),
                )
                val usernameReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.xtreamUsername,
                    onValueChange = viewModel::updateXtreamUsername,
                    label = { Text("Username") },
                    readOnly = usernameReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(usernameReadOnly),
                )
                val passwordReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.xtreamPassword,
                    onValueChange = viewModel::updateXtreamPassword,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    readOnly = passwordReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(passwordReadOnly),
                )
                val xmlTvUrlReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.xtreamXmlTvUrlOverride,
                    onValueChange = viewModel::updateXtreamXmlTvUrlOverride,
                    label = { Text("EPG (XMLTV) URL override - optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    readOnly = xmlTvUrlReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(xmlTvUrlReadOnly),
                )
            } else {
                val playlistUrlReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.m3uPlaylistUrl,
                    onValueChange = viewModel::updateM3uPlaylistUrl,
                    label = { Text("Playlist URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    readOnly = playlistUrlReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(playlistUrlReadOnly),
                )
                val epgUrlReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.m3uEpgUrl,
                    onValueChange = viewModel::updateM3uEpgUrl,
                    label = { Text("EPG (XMLTV) URL - optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    readOnly = epgUrlReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(epgUrlReadOnly),
                )
            }

            Button(onClick = { viewModel.save(); onDone() }, modifier = Modifier.tvScrollsIntoViewOnFocus()) {
                Text("Save")
            }

            if (uiState.saved) {
                Text("Saved.")
            }

            if (uiState.hasSource) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Palette.Border)

                Text("Playlist not showing everything it should? Refetch it without re-entering your details.", color = Palette.TextMuted)

                OutlinedButton(
                    onClick = viewModel::updatePlaylist,
                    enabled = !uiState.isUpdating,
                    modifier = Modifier.tvScrollsIntoViewOnFocus(),
                ) {
                    if (uiState.isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Updating…", modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("Update Playlist")
                    }
                }

                if (uiState.updated && !uiState.isUpdating) {
                    Text("Playlist updated.", color = Palette.TextMuted)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Palette.Border)

                Text("Auto-update", color = Palette.TextPrimary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = uiState.autoUpdate.mode == AutoUpdateMode.OFF,
                        onClick = { viewModel.selectAutoUpdateMode(AutoUpdateMode.OFF) },
                        label = { Text("Off") },
                        modifier = Modifier.tvScrollsIntoViewOnFocus(),
                    )
                    FilterChip(
                        selected = uiState.autoUpdate.mode == AutoUpdateMode.ON_APP_OPEN,
                        onClick = { viewModel.selectAutoUpdateMode(AutoUpdateMode.ON_APP_OPEN) },
                        label = { Text("On App Open") },
                        modifier = Modifier.tvScrollsIntoViewOnFocus(),
                    )
                    FilterChip(
                        selected = uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_DAYS,
                        onClick = { viewModel.selectAutoUpdateMode(AutoUpdateMode.EVERY_N_DAYS) },
                        label = { Text("Every few days") },
                        modifier = Modifier.tvScrollsIntoViewOnFocus(),
                    )
                    FilterChip(
                        selected = uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_HOURS,
                        onClick = { viewModel.selectAutoUpdateMode(AutoUpdateMode.EVERY_N_HOURS) },
                        label = { Text("Every few hours") },
                        modifier = Modifier.tvScrollsIntoViewOnFocus(),
                    )
                }

                if (uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_DAYS) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AUTO_UPDATE_DAY_OPTIONS.forEach { days ->
                            FilterChip(
                                selected = uiState.autoUpdate.days == days,
                                onClick = { viewModel.setAutoUpdateDays(days) },
                                label = { Text(if (days == 1) "1 day" else "$days days") },
                                modifier = Modifier.tvScrollsIntoViewOnFocus(),
                            )
                        }
                    }
                }

                if (uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_HOURS) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AUTO_UPDATE_HOUR_OPTIONS.forEach { hours ->
                            FilterChip(
                                selected = uiState.autoUpdate.hours == hours,
                                onClick = { viewModel.setAutoUpdateHours(hours) },
                                label = { Text("$hours hours") },
                                modifier = Modifier.tvScrollsIntoViewOnFocus(),
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Palette.Border)

                Text("Cache duration", color = Palette.TextPrimary)
                Text(
                    "How long the channel list and EPG guide stay cached before checking again - longer means faster loads, shorter means fresher data.",
                    color = Palette.TextMuted,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    CACHE_DURATION_DAY_OPTIONS.forEach { days ->
                        FilterChip(
                            selected = uiState.cacheDurationDays == days,
                            onClick = { viewModel.setCacheDurationDays(days) },
                            label = { Text(if (days == 1) "1 day" else "$days days") },
                            modifier = Modifier.tvScrollsIntoViewOnFocus(),
                        )
                    }
                }
            }
        }
    }
    }
}

/** TV-native sibling of IptvSettingsScreen - same IptvSettingsViewModel, TvSettingsSection/Row/TextField layout instead of a Material3 form. */
@Composable
fun IptvSettingsScreenTv(
    onDone: () -> Unit,
    viewModel: IptvSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val firstChipFocusRequester = rememberTvSettingsInitialFocus()

    Column(modifier = Modifier.fillMaxSize()) {
        TvSettingsTopBar(title = "IPTV source", onBack = onDone)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            TvSettingsSection(title = "Source type") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(20.dp)) {
                    TvChoiceChip(
                        label = "Xtream Codes",
                        selected = uiState.providerType == IptvProviderType.XTREAM,
                        modifier = Modifier.focusRequester(firstChipFocusRequester),
                        onClick = { viewModel.selectProviderType(IptvProviderType.XTREAM) },
                    )
                    TvChoiceChip(
                        label = "M3U playlist",
                        selected = uiState.providerType == IptvProviderType.M3U,
                        onClick = { viewModel.selectProviderType(IptvProviderType.M3U) },
                    )
                }
            }

            TvSettingsSection(title = if (uiState.providerType == IptvProviderType.XTREAM) "Xtream details" else "M3U details") {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (uiState.providerType == IptvProviderType.XTREAM) {
                        TvSettingsTextField(
                            value = uiState.xtreamBaseUrl,
                            onValueChange = viewModel::updateXtreamBaseUrl,
                            label = "Server URL (e.g. http://host:port)",
                            keyboardType = KeyboardType.Uri,
                        )
                        TvSettingsTextField(
                            value = uiState.xtreamUsername,
                            onValueChange = viewModel::updateXtreamUsername,
                            label = "Username",
                        )
                        TvSettingsTextField(
                            value = uiState.xtreamPassword,
                            onValueChange = viewModel::updateXtreamPassword,
                            label = "Password",
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        TvSettingsTextField(
                            value = uiState.xtreamXmlTvUrlOverride,
                            onValueChange = viewModel::updateXtreamXmlTvUrlOverride,
                            label = "EPG (XMLTV) URL override - optional",
                            keyboardType = KeyboardType.Uri,
                        )
                    } else {
                        TvSettingsTextField(
                            value = uiState.m3uPlaylistUrl,
                            onValueChange = viewModel::updateM3uPlaylistUrl,
                            label = "Playlist URL",
                            keyboardType = KeyboardType.Uri,
                        )
                        TvSettingsTextField(
                            value = uiState.m3uEpgUrl,
                            onValueChange = viewModel::updateM3uEpgUrl,
                            label = "EPG (XMLTV) URL - optional",
                            keyboardType = KeyboardType.Uri,
                        )
                    }
                    TvButton(onClick = { viewModel.save(); onDone() }) { TvText(if (uiState.saved) "Saved" else "Save") }
                }
            }

            if (uiState.hasSource) {
                TvSettingsSection(title = "Playlist") {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvText("Playlist not showing everything it should? Refetch it without re-entering your details.", color = Palette.TextMuted)
                        TvButton(onClick = viewModel::updatePlaylist) {
                            TvText(if (uiState.isUpdating) "Updating…" else "Update Playlist")
                        }
                        if (uiState.updated && !uiState.isUpdating) {
                            TvText("Playlist updated.", color = Palette.TextMuted)
                        }
                    }
                }

                TvSettingsSection(title = "Auto-update") {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvChoiceChip("Off", uiState.autoUpdate.mode == AutoUpdateMode.OFF) { viewModel.selectAutoUpdateMode(AutoUpdateMode.OFF) }
                            TvChoiceChip("On App Open", uiState.autoUpdate.mode == AutoUpdateMode.ON_APP_OPEN) { viewModel.selectAutoUpdateMode(AutoUpdateMode.ON_APP_OPEN) }
                            TvChoiceChip("Every few days", uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_DAYS) { viewModel.selectAutoUpdateMode(AutoUpdateMode.EVERY_N_DAYS) }
                            TvChoiceChip("Every few hours", uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_HOURS) { viewModel.selectAutoUpdateMode(AutoUpdateMode.EVERY_N_HOURS) }
                        }
                        if (uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_DAYS) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AUTO_UPDATE_DAY_OPTIONS.forEach { days ->
                                    TvChoiceChip(if (days == 1) "1 day" else "$days days", uiState.autoUpdate.days == days) { viewModel.setAutoUpdateDays(days) }
                                }
                            }
                        }
                        if (uiState.autoUpdate.mode == AutoUpdateMode.EVERY_N_HOURS) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AUTO_UPDATE_HOUR_OPTIONS.forEach { hours ->
                                    TvChoiceChip("$hours hours", uiState.autoUpdate.hours == hours) { viewModel.setAutoUpdateHours(hours) }
                                }
                            }
                        }
                    }
                }

                TvSettingsSection(title = "Cache duration") {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvText(
                            "How long the channel list and EPG guide stay cached before checking again - longer means faster loads, shorter means fresher data.",
                            color = Palette.TextMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CACHE_DURATION_DAY_OPTIONS.forEach { days ->
                                TvChoiceChip(if (days == 1) "1 day" else "$days days", uiState.cacheDurationDays == days) { viewModel.setCacheDurationDays(days) }
                            }
                        }
                    }
                }
            }
        }
    }
}
