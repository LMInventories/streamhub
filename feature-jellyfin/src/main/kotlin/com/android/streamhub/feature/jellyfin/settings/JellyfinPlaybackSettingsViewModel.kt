package com.android.streamhub.feature.jellyfin.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinAppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JellyfinPlaybackSettingsUiState(
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val maxStreamingBitrateMbps: Int? = null,
)

@HiltViewModel
class JellyfinPlaybackSettingsViewModel @Inject constructor(
    private val repository: JellyfinAppSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<JellyfinPlaybackSettingsUiState> = repository.settingsFlow
        .map { JellyfinPlaybackSettingsUiState(it.preferredAudioLanguage, it.preferredSubtitleLanguage, it.maxStreamingBitrateMbps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JellyfinPlaybackSettingsUiState())

    fun setPreferredAudioLanguage(code: String?) {
        viewModelScope.launch { repository.update { it.copy(preferredAudioLanguage = code) } }
    }

    fun setPreferredSubtitleLanguage(code: String?) {
        viewModelScope.launch { repository.update { it.copy(preferredSubtitleLanguage = code) } }
    }

    fun setMaxStreamingBitrateMbps(mbps: Int?) {
        viewModelScope.launch { repository.update { it.copy(maxStreamingBitrateMbps = mbps) } }
    }
}
