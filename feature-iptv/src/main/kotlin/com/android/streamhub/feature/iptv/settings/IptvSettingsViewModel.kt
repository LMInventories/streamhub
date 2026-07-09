package com.android.streamhub.feature.iptv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.AutoUpdateMode
import com.android.streamhub.feature.iptv.data.AutoUpdateSetting
import com.android.streamhub.feature.iptv.data.IptvAutoUpdateRepository
import com.android.streamhub.feature.iptv.data.IptvSourceConfig
import com.android.streamhub.feature.iptv.data.IptvSourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class IptvProviderType { XTREAM, M3U }

data class IptvSettingsUiState(
    val providerType: IptvProviderType = IptvProviderType.XTREAM,
    val xtreamBaseUrl: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = "",
    val xtreamXmlTvUrlOverride: String = "",
    val m3uPlaylistUrl: String = "",
    val m3uEpgUrl: String = "",
    val saved: Boolean = false,
    val hasSource: Boolean = false,
    val isUpdating: Boolean = false,
    val updated: Boolean = false,
    val autoUpdate: AutoUpdateSetting = AutoUpdateSetting(),
)

@HiltViewModel
class IptvSettingsViewModel @Inject constructor(
    private val repository: IptvSourceConfigRepository,
    private val autoUpdateRepository: IptvAutoUpdateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IptvSettingsUiState())
    val uiState: StateFlow<IptvSettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            when (val config = repository.configFlow.first()) {
                is IptvSourceConfig.Xtream -> _uiState.update {
                    it.copy(
                        providerType = IptvProviderType.XTREAM,
                        xtreamBaseUrl = config.baseUrl,
                        xtreamUsername = config.username,
                        xtreamPassword = config.password,
                        xtreamXmlTvUrlOverride = config.xmlTvUrlOverride.orEmpty(),
                        hasSource = true,
                    )
                }
                is IptvSourceConfig.M3u -> _uiState.update {
                    it.copy(
                        providerType = IptvProviderType.M3U,
                        m3uPlaylistUrl = config.playlistUrl,
                        m3uEpgUrl = config.epgUrl.orEmpty(),
                        hasSource = true,
                    )
                }
                null -> Unit
            }
        }
        viewModelScope.launch {
            autoUpdateRepository.settingFlow.collect { setting -> _uiState.update { it.copy(autoUpdate = setting) } }
        }
    }

    /** Saves immediately, not bundled into the source-config Save button - this is a standalone background-behavior toggle. */
    fun selectAutoUpdateMode(mode: AutoUpdateMode) = saveAutoUpdate { it.copy(mode = mode) }
    fun setAutoUpdateDays(days: Int) = saveAutoUpdate { it.copy(days = days) }
    fun setAutoUpdateHours(hours: Int) = saveAutoUpdate { it.copy(hours = hours) }

    private fun saveAutoUpdate(transform: (AutoUpdateSetting) -> AutoUpdateSetting) {
        val updated = transform(_uiState.value.autoUpdate)
        _uiState.update { it.copy(autoUpdate = updated) }
        viewModelScope.launch { autoUpdateRepository.saveSetting(updated) }
    }

    fun selectProviderType(type: IptvProviderType) = _uiState.update { it.copy(providerType = type, saved = false) }
    fun updateXtreamBaseUrl(value: String) = _uiState.update { it.copy(xtreamBaseUrl = value, saved = false) }
    fun updateXtreamUsername(value: String) = _uiState.update { it.copy(xtreamUsername = value, saved = false) }
    fun updateXtreamPassword(value: String) = _uiState.update { it.copy(xtreamPassword = value, saved = false) }
    fun updateXtreamXmlTvUrlOverride(value: String) = _uiState.update { it.copy(xtreamXmlTvUrlOverride = value, saved = false) }
    fun updateM3uPlaylistUrl(value: String) = _uiState.update { it.copy(m3uPlaylistUrl = value, saved = false) }
    fun updateM3uEpgUrl(value: String) = _uiState.update { it.copy(m3uEpgUrl = value, saved = false) }

    fun save() {
        val state = _uiState.value
        val config = when (state.providerType) {
            IptvProviderType.XTREAM -> IptvSourceConfig.Xtream(
                baseUrl = state.xtreamBaseUrl.trim(),
                username = state.xtreamUsername.trim(),
                password = state.xtreamPassword.trim(),
                xmlTvUrlOverride = state.xtreamXmlTvUrlOverride.trim().takeIf(String::isNotEmpty),
            )
            IptvProviderType.M3U -> IptvSourceConfig.M3u(
                playlistUrl = state.m3uPlaylistUrl.trim(),
                epgUrl = state.m3uEpgUrl.trim().takeIf { it.isNotEmpty() },
            )
        }
        viewModelScope.launch {
            repository.save(config)
            _uiState.update { it.copy(saved = true, hasSource = true) }
        }
    }

    /** Unconditional full refresh (see IptvAutoUpdateRepository.forceRefreshNow) - without this, active Live TV/VOD screens would keep showing whatever they last loaded until the app was restarted. */
    fun updatePlaylist() {
        if (!_uiState.value.hasSource) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, updated = false) }
            autoUpdateRepository.forceRefreshNow()
            _uiState.update { it.copy(isUpdating = false, updated = true) }
        }
    }
}
