package com.android.streamhub.feature.emby.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyRemoteDataSource
import com.android.streamhub.feature.emby.data.EmbySourceConfig
import com.android.streamhub.feature.emby.data.EmbySourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmbySettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val signedIn: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Sign-in form (server URL, username, password) + sign-out for an Emby source. Deliberately no
 * Quick Connect - unlike Jellyfin's SDK, Emby's classic API has no equivalent short-code approval
 * flow to authenticate against, so AuthenticateByName is the only sign-in path this module offers.
 */
@HiltViewModel
class EmbySettingsViewModel @Inject constructor(
    private val remoteDataSource: EmbyRemoteDataSource,
    private val configRepository: EmbySourceConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmbySettingsUiState())
    val uiState: StateFlow<EmbySettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            configRepository.configFlow.first()?.let { config ->
                _uiState.update { it.copy(serverUrl = config.serverUrl, username = config.username, signedIn = true) }
            }
        }
    }

    fun updateServerUrl(value: String) = _uiState.update { it.copy(serverUrl = value, signedIn = false, errorMessage = null) }
    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, signedIn = false, errorMessage = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, signedIn = false, errorMessage = null) }

    /**
     * Authenticates immediately rather than just saving raw fields - AuthenticateByName gives an
     * immediate pass/fail signal, and only the resulting access token (not the password) gets
     * persisted, so there's no reason to defer the check to whenever something first browses.
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
            runCatching {
                remoteDataSource.authenticateByName(serverUrl, state.username, state.password)
            }.onSuccess { result ->
                val accessToken = result.accessToken
                val userId = result.user?.id
                if (accessToken != null && userId != null) {
                    configRepository.save(
                        EmbySourceConfig(
                            serverUrl = serverUrl,
                            username = state.username,
                            userId = userId,
                            accessToken = accessToken,
                            serverName = fetchServerName(serverUrl),
                        ),
                    )
                    _uiState.update { it.copy(isSigningIn = false, signedIn = true, password = "") }
                } else {
                    _uiState.update { it.copy(isSigningIn = false, errorMessage = "Server didn't return a valid session") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSigningIn = false, errorMessage = e.describeChain()) }
            }
        }
    }

    // getPublicSystemInfo() is unauthenticated, so this works regardless of the token just minted.
    // Best-effort, not surfaced as a sign-in error - the friendly server name is only used for a
    // Home screen label, not worth failing an otherwise-successful sign-in over.
    private suspend fun fetchServerName(serverUrl: String): String? =
        runCatching { remoteDataSource.getPublicSystemInfo(serverUrl).serverName }.getOrNull()

    fun signOut() {
        viewModelScope.launch {
            configRepository.clear()
            _uiState.update { EmbySettingsUiState() }
        }
    }

    /**
     * Retrofit/OkHttp exceptions (e.g. HttpException) often only carry a generic message like
     * "HTTP 500" - that just confirms the server responded with an error, not why. The underlying
     * cause chain is where the actually useful detail lives - surfacing it here rather than
     * swallowing it is the difference between "sign-in failed" and something a user can actually
     * act on or report.
     */
    private fun Throwable.describeChain(): String {
        val messages = generateSequence(this) { it.cause }
            .mapNotNull { it.message }
            .distinct()
            .toList()
        return messages.ifEmpty { listOf("Sign-in failed") }.joinToString(" — ")
    }
}
