package com.android.streamhub.feature.iptv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("IPTV source") })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
