package com.android.streamhub.feature.jellyfin.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette

// Same "wrap own MaterialTheme locally" reasoning as IptvSettingsScreen - reachable from the TV
// nav host too, which only wraps content in tv-material3's MaterialTheme, not this one.
private val JellyfinSettingsColorScheme = darkColorScheme(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinSettingsScreen(
    onDone: () -> Unit,
    viewModel: JellyfinSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    MaterialTheme(colorScheme = JellyfinSettingsColorScheme) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Jellyfin server") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = viewModel::updateServerUrl,
                    label = { Text("Server URL (e.g. http://host:8096)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !uiState.isSigningIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::updateUsername,
                    label = { Text("Username") },
                    enabled = !uiState.isSigningIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !uiState.isSigningIn,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(onClick = viewModel::signIn, enabled = !uiState.isSigningIn) {
                    if (uiState.isSigningIn) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Signing in…", modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("Sign in")
                    }
                }

                uiState.errorMessage?.let { error ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(error, color = Palette.Error, modifier = Modifier.weight(1f))
                        // Screenshots of this screen can end up with lines auto-redacted by the
                        // OS if they pattern-match as sensitive (an "Authorization:" line is a
                        // common trigger) even though the live on-screen text isn't - copying the
                        // raw text out sidesteps that when this needs to be shared for debugging.
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(error)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy error details")
                        }
                    }
                }

                if (uiState.signedIn) {
                    Text("Signed in as ${uiState.username}.", color = Palette.TextMuted)
                    OutlinedButton(onClick = { viewModel.signOut() }) {
                        Text("Sign out")
                    }
                }
            }
        }
    }
}
