package com.android.streamhub.feature.emby.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyConnectRemoteDataSource
import com.android.streamhub.feature.emby.data.EmbyConnectServerDto
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
    // Emby Connect (cloud account-linking sign-in) form state - a secondary sign-in path
    // alongside the direct form above, see signInWithConnect()'s doc.
    val connectEmail: String = "",
    val connectPassword: String = "",
    val isConnectSigningIn: Boolean = false,
    // Only populated when discovery finds more than one linked server and the user needs to pick
    // one - a single linked server auto-proceeds without ever touching this.
    val connectServers: List<EmbyConnectServerDto> = emptyList(),
    val connectErrorMessage: String? = null,
)

/**
 * Sign-in form (server URL, username, password) + sign-out for an Emby source, plus a secondary
 * Emby Connect (cloud account-linking) sign-in path - see signInWithConnect()'s doc. Unlike
 * Jellyfin's Quick Connect (a PIN-pairing flow for local-network sign-in that Emby has no
 * equivalent of), Emby Connect is Emby's own distinct system: it authenticates a cloud account
 * that may be linked to one or more independently-hosted servers, rather than pairing with an
 * already-known local server.
 */
@HiltViewModel
class EmbySettingsViewModel @Inject constructor(
    private val remoteDataSource: EmbyRemoteDataSource,
    private val connectRemoteDataSource: EmbyConnectRemoteDataSource,
    private val configRepository: EmbySourceConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmbySettingsUiState())
    val uiState: StateFlow<EmbySettingsUiState> = _uiState

    // Carried from signInWithConnect()'s discovery step to selectConnectServer() when the user
    // has to pick from more than one linked server - step 3 (token exchange) needs the Connect
    // user id and the account's username again, and neither is otherwise available once the UI
    // state's connectServers picker is showing.
    private var pendingConnectUserId: String? = null
    private var pendingConnectUsername: String = ""

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

    fun onConnectEmailChange(value: String) = _uiState.update { it.copy(connectEmail = value, connectErrorMessage = null) }
    fun onConnectPasswordChange(value: String) = _uiState.update { it.copy(connectPassword = value, connectErrorMessage = null) }

    /**
     * Emby Connect's three-step flow (see EmbyConnectRemoteDataSource's doc): authenticate the
     * cloud account, discover which server(s) it's linked to, then exchange for a token local to
     * whichever server is chosen. A single linked server auto-proceeds straight through step 3 -
     * the picker (uiState.connectServers) only needs to appear when there's an actual choice.
     */
    fun signInWithConnect() {
        val state = _uiState.value
        val email = state.connectEmail.trim()
        if (email.isEmpty() || state.connectPassword.isBlank()) {
            _uiState.update { it.copy(connectErrorMessage = "Email and password are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectSigningIn = true, connectErrorMessage = null, connectServers = emptyList()) }
            runCatching {
                val auth = connectRemoteDataSource.authenticate(email, state.connectPassword)
                val connectUserId = auth.connectUserId
                val connectAccessToken = auth.connectAccessToken
                require(connectUserId != null && connectAccessToken != null) { "Emby Connect didn't return a valid session" }
                connectUserId to connectRemoteDataSource.getServers(connectUserId, connectAccessToken)
            }.onSuccess { (connectUserId, servers) ->
                pendingConnectUserId = connectUserId
                pendingConnectUsername = email
                when {
                    servers.isEmpty() -> _uiState.update {
                        it.copy(isConnectSigningIn = false, connectErrorMessage = "No servers linked to this Emby Connect account.")
                    }
                    servers.size == 1 -> finishConnectSignIn(connectUserId, servers.first(), email)
                    else -> _uiState.update { it.copy(isConnectSigningIn = false, connectServers = servers) }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isConnectSigningIn = false, connectErrorMessage = e.describeChain()) }
            }
        }
    }

    /** Called from the server picker once signInWithConnect()'s discovery step found more than one linked server. */
    fun selectConnectServer(server: EmbyConnectServerDto) {
        val connectUserId = pendingConnectUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectSigningIn = true, connectErrorMessage = null) }
            finishConnectSignIn(connectUserId, server, pendingConnectUsername)
        }
    }

    /**
     * Step 3 (token exchange) plus the same save-config-and-reset-form finish direct signIn()
     * does - both sign-in paths converge on the same EmbySourceConfig, so everything downstream
     * (browsing, playback) behaves identically regardless of which path produced it. Prefers
     * LocalAddress over Url (see EmbyConnectServerDto's doc) since this app has no separate
     * "remote vs local" concept elsewhere.
     */
    private suspend fun finishConnectSignIn(connectUserId: String, server: EmbyConnectServerDto, email: String) {
        val accessKey = server.accessKey
        val serverUrl = server.localAddress.takeUnless { it.isNullOrBlank() } ?: server.url
        if (accessKey == null || serverUrl.isNullOrBlank()) {
            _uiState.update { it.copy(isConnectSigningIn = false, connectErrorMessage = "Emby Connect server entry is missing required fields") }
            return
        }
        val trimmedServerUrl = serverUrl.trim().trimEnd('/')
        runCatching {
            connectRemoteDataSource.exchangeToken(trimmedServerUrl, accessKey, connectUserId)
        }.onSuccess { result ->
            val accessToken = result.accessToken
            val userId = result.localUserId
            if (accessToken != null && userId != null) {
                configRepository.save(
                    EmbySourceConfig(
                        serverUrl = trimmedServerUrl,
                        username = email,
                        userId = userId,
                        accessToken = accessToken,
                        serverName = server.name ?: fetchServerName(trimmedServerUrl),
                    ),
                )
                pendingConnectUserId = null
                _uiState.update {
                    it.copy(
                        isConnectSigningIn = false,
                        signedIn = true,
                        serverUrl = trimmedServerUrl,
                        username = email,
                        connectEmail = "",
                        connectPassword = "",
                        connectServers = emptyList(),
                    )
                }
            } else {
                _uiState.update { it.copy(isConnectSigningIn = false, connectErrorMessage = "Server didn't return a valid session") }
            }
        }.onFailure { e ->
            _uiState.update { it.copy(isConnectSigningIn = false, connectErrorMessage = e.describeChain()) }
        }
    }

    fun signOut() {
        pendingConnectUserId = null
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
