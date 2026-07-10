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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.dpadMovesFocusVertically
import com.android.streamhub.core.design.tvManualKeyboard
import com.android.streamhub.core.design.tvScrollsIntoViewOnFocus
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.iptv.data.AUTO_UPDATE_DAY_OPTIONS
import com.android.streamhub.feature.iptv.data.AUTO_UPDATE_HOUR_OPTIONS
import com.android.streamhub.feature.iptv.data.AutoUpdateMode

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
                OutlinedTextField(
                    value = uiState.xtreamBaseUrl,
                    onValueChange = viewModel::updateXtreamBaseUrl,
                    label = { Text("Server URL (e.g. http://host:port)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(),
                )
                OutlinedTextField(
                    value = uiState.xtreamUsername,
                    onValueChange = viewModel::updateXtreamUsername,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(),
                )
                OutlinedTextField(
                    value = uiState.xtreamPassword,
                    onValueChange = viewModel::updateXtreamPassword,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(),
                )
                OutlinedTextField(
                    value = uiState.xtreamXmlTvUrlOverride,
                    onValueChange = viewModel::updateXtreamXmlTvUrlOverride,
                    label = { Text("EPG (XMLTV) URL override - optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(),
                )
            } else {
                OutlinedTextField(
                    value = uiState.m3uPlaylistUrl,
                    onValueChange = viewModel::updateM3uPlaylistUrl,
                    label = { Text("Playlist URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(),
                )
                OutlinedTextField(
                    value = uiState.m3uEpgUrl,
                    onValueChange = viewModel::updateM3uEpgUrl,
                    label = { Text("EPG (XMLTV) URL - optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(),
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
            }
        }
    }
    }
}
