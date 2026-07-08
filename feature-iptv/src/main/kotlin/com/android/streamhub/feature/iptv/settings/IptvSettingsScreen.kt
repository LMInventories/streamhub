package com.android.streamhub.feature.iptv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette

// This screen uses mobile Material3 components but is reachable from the TV nav host too, which
// only wraps content in tv-material3's MaterialTheme, not this one - without a local wrapper the
// text fields/buttons would fall back to M3's default light scheme when opened from TV. Same
// reasoning as EpgGridPanel/PlayerScreenTv's dialogs wrapping themselves rather than relying on
// an ambient theme they can't count on.
private val IptvSettingsColorScheme = darkColorScheme(
    primary = Palette.Accent,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceElevated,
    onSurfaceVariant = Palette.TextMuted,
    outline = Palette.Border,
    error = Palette.Error,
)

/**
 * Shared across phone and TV - it's a form, and standard Material3 text fields are already
 * D-pad-focusable, so there's no TV-specific idiom worth a second implementation here (unlike
 * the browse/player screens, which genuinely differ by form factor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvSettingsScreen(
    onDone: () -> Unit,
    viewModel: IptvSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = IptvSettingsColorScheme) {
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
                )
                FilterChip(
                    selected = uiState.providerType == IptvProviderType.M3U,
                    onClick = { viewModel.selectProviderType(IptvProviderType.M3U) },
                    label = { Text("M3U playlist") },
                )
            }

            if (uiState.providerType == IptvProviderType.XTREAM) {
                OutlinedTextField(
                    value = uiState.xtreamBaseUrl,
                    onValueChange = viewModel::updateXtreamBaseUrl,
                    label = { Text("Server URL (e.g. http://host:port)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.xtreamUsername,
                    onValueChange = viewModel::updateXtreamUsername,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.xtreamPassword,
                    onValueChange = viewModel::updateXtreamPassword,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = uiState.m3uPlaylistUrl,
                    onValueChange = viewModel::updateM3uPlaylistUrl,
                    label = { Text("Playlist URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.m3uEpgUrl,
                    onValueChange = viewModel::updateM3uEpgUrl,
                    label = { Text("EPG (XMLTV) URL - optional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(onClick = { viewModel.save(); onDone() }) {
                Text("Save")
            }

            if (uiState.saved) {
                Text("Saved.")
            }
        }
    }
    }
}
