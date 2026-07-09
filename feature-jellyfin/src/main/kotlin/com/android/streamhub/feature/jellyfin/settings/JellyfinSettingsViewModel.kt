package com.android.streamhub.feature.jellyfin.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfig
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfigRepository
import com.android.streamhub.feature.jellyfin.di.JellyfinDebugInterceptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userApi
import javax.inject.Inject

private const val QUICK_CONNECT_POLL_INTERVAL_MS = 3_000L
private const val QUICK_CONNECT_TIMEOUT_MS = 5 * 60_000L

data class JellyfinSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val signedIn: Boolean = false,
    val errorMessage: String? = null,
    val isQuickConnecting: Boolean = false,
    val quickConnectCode: String? = null,
)

@HiltViewModel
class JellyfinSettingsViewModel @Inject constructor(
    private val jellyfin: Jellyfin,
    private val repository: JellyfinSourceConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinSettingsUiState())
    val uiState: StateFlow<JellyfinSettingsUiState> = _uiState
    private var quickConnectJob: Job? = null

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
                val exchange = JellyfinDebugInterceptor.lastExchange?.let { "\n\n$it" }.orEmpty()
                _uiState.update { it.copy(isSigningIn = false, errorMessage = "${e.describeChain()}$exchange") }
            }
        }
    }

    /**
     * Authenticates via a short approval code entered into an already-logged-in session (the web
     * UI, or another app) instead of sending the username/password directly - a distinct server
     * code path from signIn()'s AuthenticateByName that doesn't look the account up by username
     * string at all, so it sidesteps whatever's specifically broken in that lookup for a given
     * account rather than requiring server-side access to actually fix it.
     */
    fun startQuickConnect() {
        val serverUrl = _uiState.value.serverUrl.trim().trimEnd('/')
        if (serverUrl.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Server URL is required") }
            return
        }
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            _uiState.update { it.copy(isQuickConnecting = true, errorMessage = null, quickConnectCode = null) }
            val api = jellyfin.createApi(baseUrl = serverUrl)
            runCatching { api.quickConnectApi.initiateQuickConnect().content }
                .onSuccess { initial ->
                    _uiState.update { it.copy(quickConnectCode = initial.code) }
                    pollQuickConnect(api, serverUrl, initial.secret)
                }
                .onFailure { e ->
                    val exchange = JellyfinDebugInterceptor.lastExchange?.let { "\n\n$it" }.orEmpty()
                    _uiState.update { it.copy(isQuickConnecting = false, errorMessage = "Quick Connect unavailable: ${e.describeChain()}$exchange") }
                }
        }
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        _uiState.update { it.copy(isQuickConnecting = false, quickConnectCode = null) }
    }

    private suspend fun pollQuickConnect(api: ApiClient, serverUrl: String, secret: String) {
        val deadline = System.currentTimeMillis() + QUICK_CONNECT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(QUICK_CONNECT_POLL_INTERVAL_MS)
            val state = runCatching { api.quickConnectApi.getQuickConnectState(secret).content }.getOrNull()
            if (state?.authenticated == true) {
                completeQuickConnect(api, serverUrl, secret)
                return
            }
        }
        _uiState.update { it.copy(isQuickConnecting = false, quickConnectCode = null, errorMessage = "Quick Connect code expired - try again") }
    }

    private suspend fun completeQuickConnect(api: ApiClient, serverUrl: String, secret: String) {
        runCatching { api.userApi.authenticateWithQuickConnect(secret).content }
            .onSuccess { result ->
                val accessToken = result.accessToken
                val userId = result.user?.id?.toString()
                val username = result.user?.name.orEmpty()
                if (accessToken != null && userId != null) {
                    repository.save(
                        JellyfinSourceConfig(serverUrl = serverUrl, username = username, userId = userId, accessToken = accessToken),
                    )
                    _uiState.update {
                        it.copy(isQuickConnecting = false, quickConnectCode = null, signedIn = true, username = username, password = "")
                    }
                } else {
                    _uiState.update { it.copy(isQuickConnecting = false, quickConnectCode = null, errorMessage = "Server didn't return a valid session") }
                }
            }
            .onFailure { e ->
                val exchange = JellyfinDebugInterceptor.lastExchange?.let { "\n\n$it" }.orEmpty()
                _uiState.update { it.copy(isQuickConnecting = false, quickConnectCode = null, errorMessage = "${e.describeChain()}$exchange") }
            }
    }

    fun signOut() {
        quickConnectJob?.cancel()
        viewModelScope.launch {
            repository.clear()
            _uiState.update { JellyfinSettingsUiState() }
        }
    }

    /**
     * The SDK's own exceptions (e.g. InvalidStatusException) only carry a generic message like
     * "Invalid HTTP status in response: 500" - that just confirms the server responded with an
     * error, not why. The underlying cause chain (often a raw Ktor/OkHttp exception) is where the
     * actually useful detail lives - surfacing it here rather than swallowing it is the
     * difference between "sign-in failed" and something a user can actually act on or report.
     */
    private fun Throwable.describeChain(): String {
        val messages = generateSequence(this) { it.cause }
            .mapNotNull { it.message }
            .distinct()
            .toList()
        return messages.ifEmpty { listOf("Sign-in failed") }.joinToString(" — ")
    }
}
