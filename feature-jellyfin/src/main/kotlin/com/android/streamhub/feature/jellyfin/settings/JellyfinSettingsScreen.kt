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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.dpadMovesFocusVertically
import com.android.streamhub.core.design.rememberTvManualKeyboardReadOnly
import com.android.streamhub.core.design.tvManualKeyboard
import com.android.streamhub.core.design.tvScrollsIntoViewOnFocus
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsSection
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTextField
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTopBar
import com.android.streamhub.core.ui.tv.scaffold.rememberTvSettingsInitialFocus
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

// Same "wrap own MaterialTheme locally" reasoning as IptvSettingsScreen - reachable from the TV
// nav host too, which only wraps content in tv-material3's MaterialTheme, not this one.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinSettingsScreen(
    onDone: () -> Unit,
    viewModel: JellyfinSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    MaterialTheme(colorScheme = appColorScheme()) {
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
                val serverUrlReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = viewModel::updateServerUrl,
                    label = { Text("Server URL (e.g. http://host:8096)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !uiState.isSigningIn,
                    readOnly = serverUrlReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(serverUrlReadOnly),
                )
                val usernameReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::updateUsername,
                    label = { Text("Username") },
                    enabled = !uiState.isSigningIn,
                    readOnly = usernameReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(usernameReadOnly),
                )
                val passwordReadOnly = rememberTvManualKeyboardReadOnly()
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !uiState.isSigningIn,
                    readOnly = passwordReadOnly.value,
                    modifier = Modifier.fillMaxWidth().dpadMovesFocusVertically(focusManager).tvScrollsIntoViewOnFocus().tvManualKeyboard(passwordReadOnly),
                )

                Button(
                    onClick = viewModel::signIn,
                    enabled = !uiState.isSigningIn,
                    modifier = Modifier.tvScrollsIntoViewOnFocus(),
                ) {
                    if (uiState.isSigningIn) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Signing in…", modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("Sign in")
                    }
                }

                if (!uiState.signedIn) {
                    Text("— or —", color = Palette.TextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                    val quickConnectCode = uiState.quickConnectCode
                    if (quickConnectCode != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Enter this code on the server (web UI, or another signed-in app) under your account's Quick Connect settings:",
                                color = Palette.TextMuted,
                            )
                            Text(
                                text = quickConnectCode,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Palette.Accent,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text("Waiting for approval…", color = Palette.TextMuted)
                            }
                            OutlinedButton(onClick = viewModel::cancelQuickConnect, modifier = Modifier.tvScrollsIntoViewOnFocus()) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::startQuickConnect,
                            enabled = !uiState.isQuickConnecting,
                            modifier = Modifier.tvScrollsIntoViewOnFocus(),
                        ) {
                            if (uiState.isQuickConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text("Starting…", modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Text("Sign in with Quick Connect")
                            }
                        }
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
                    OutlinedButton(onClick = { viewModel.signOut() }, modifier = Modifier.tvScrollsIntoViewOnFocus()) {
                        Text("Sign out")
                    }
                }
            }
        }
    }
}

/** TV-native sibling of JellyfinSettingsScreen - same JellyfinSettingsViewModel (server sign-in + Quick Connect), TvSettingsSection/TextField layout instead of a Material3 form. */
@Composable
fun JellyfinSettingsScreenTv(
    onDone: () -> Unit,
    viewModel: JellyfinSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val firstFieldFocusRequester = rememberTvSettingsInitialFocus()

    Column(modifier = Modifier.fillMaxSize()) {
        TvSettingsTopBar(title = "Jellyfin server", onBack = onDone)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            TvSettingsSection(title = "Server") {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvSettingsTextField(
                        value = uiState.serverUrl,
                        onValueChange = viewModel::updateServerUrl,
                        label = "Server URL (e.g. http://host:8096)",
                        keyboardType = KeyboardType.Uri,
                        enabled = !uiState.isSigningIn,
                        modifier = Modifier.focusRequester(firstFieldFocusRequester),
                    )
                    TvSettingsTextField(
                        value = uiState.username,
                        onValueChange = viewModel::updateUsername,
                        label = "Username",
                        enabled = !uiState.isSigningIn,
                    )
                    TvSettingsTextField(
                        value = uiState.password,
                        onValueChange = viewModel::updatePassword,
                        label = "Password",
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !uiState.isSigningIn,
                    )
                    TvButton(onClick = viewModel::signIn, enabled = !uiState.isSigningIn) {
                        TvText(if (uiState.isSigningIn) "Signing in…" else "Sign in")
                    }
                }
            }

            if (!uiState.signedIn) {
                TvSettingsSection(title = "Quick Connect") {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val quickConnectCode = uiState.quickConnectCode
                        if (quickConnectCode != null) {
                            TvText(
                                text = "Enter this code on the server (web UI, or another signed-in app) under your account's Quick Connect settings:",
                                color = Palette.TextMuted,
                            )
                            TvText(
                                text = quickConnectCode,
                                style = TvMaterialTheme.typography.headlineMedium,
                                color = Palette.Accent,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                            TvText("Waiting for approval…", color = Palette.TextMuted)
                            TvButton(onClick = viewModel::cancelQuickConnect) { TvText("Cancel") }
                        } else {
                            TvButton(onClick = viewModel::startQuickConnect, enabled = !uiState.isQuickConnecting) {
                                TvText(if (uiState.isQuickConnecting) "Starting…" else "Sign in with Quick Connect")
                            }
                        }
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                TvSettingsSection(title = "Error") {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(20.dp)) {
                        TvText(error, color = Palette.Error, modifier = Modifier.weight(1f))
                        TvIconButton(onClick = { clipboardManager.setText(AnnotatedString(error)) }) {
                            TvIcon(Icons.Filled.ContentCopy, contentDescription = "Copy error details")
                        }
                    }
                }
            }

            if (uiState.signedIn) {
                TvSettingsSection(title = "Account") {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvText("Signed in as ${uiState.username}.", color = Palette.TextMuted)
                        TvButton(onClick = { viewModel.signOut() }) { TvText("Sign out") }
                    }
                }
            }
        }
    }
}
