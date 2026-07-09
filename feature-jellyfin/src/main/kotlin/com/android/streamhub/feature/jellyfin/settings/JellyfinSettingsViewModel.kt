package com.android.streamhub.feature.jellyfin.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfig
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import javax.inject.Inject

data class JellyfinSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val signedIn: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class JellyfinSettingsViewModel @Inject constructor(
    private val jellyfin: Jellyfin,
    private val repository: JellyfinSourceConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinSettingsUiState())
    val uiState: StateFlow<JellyfinSettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.configFlow.first()?.let { config ->
                _uiState.update { it.copy(serverUrl = config.serverUrl, username = config.username, signedIn = true) }
            }
        }
    }

    fun updateServerUrl(value: String) = _uiState.update { it.copy(serverUrl = value, signedIn = false, errorMessage = null) }
    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, signedIn = false, errorMessage = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, signedIn = false, errorMessage = null) }

    /**
     * Authenticates immediately rather than just saving raw fields (unlike IptvSettingsViewModel,
     * which only validates credentials later when something actually browses) - the SDK gives an
     * immediate pass/fail signal here, and only the resulting access token (not the password)
     * gets persisted, so there's no reason to defer the check.
     */
    fun signIn() {
        val state = _uiState.value
        val serverUrl = state.serverUrl.trim().trimEnd('/')
        if (serverUrl.isEmpty() || state.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Server URL and username are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, errorMessage = null) }
            val api = jellyfin.createApi(baseUrl = serverUrl)
            runCatching {
                api.userApi.authenticateUserByName(username = state.username, password = state.password).content
            }.onSuccess { result ->
                val accessToken = result.accessToken
                val userId = result.user?.id?.toString()
                if (accessToken != null && userId != null) {
                    repository.save(
                        JellyfinSourceConfig(serverUrl = serverUrl, username = state.username, userId = userId, accessToken = accessToken),
                    )
                    _uiState.update { it.copy(isSigningIn = false, signedIn = true, password = "") }
                } else {
                    _uiState.update { it.copy(isSigningIn = false, errorMessage = "Server didn't return a valid session") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSigningIn = false, errorMessage = e.message ?: "Sign-in failed") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.clear()
            _uiState.update { JellyfinSettingsUiState() }
        }
    }
}
