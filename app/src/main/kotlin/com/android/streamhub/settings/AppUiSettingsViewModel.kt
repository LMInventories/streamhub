package com.android.streamhub.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.design.ThemeMode
import com.android.streamhub.feature.iptv.data.IptvAppSettingsRepository
import com.android.streamhub.feature.iptv.data.PreviewPlayerSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUiSettingsViewModel @Inject constructor(
    private val repository: AppUiSettingsRepository,
    // Preview player size is conceptually an appearance setting, but the value itself has to stay
    // on IptvAppSettings/IptvAppSettingsRepository (feature-iptv) - LiveTvViewModel reads it from
    // there, and feature modules can't depend on :app (where AppUiSettings lives) to read it back.
    private val iptvAppSettingsRepository: IptvAppSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AppUiSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiSettings())

    val previewPlayerSize: StateFlow<PreviewPlayerSize> = iptvAppSettingsRepository.settingsFlow
        .map { it.previewPlayerSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewPlayerSize.MEDIUM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.update { it.copy(themeMode = mode) } }
    }

    fun setTextScale(scale: TextScale) {
        viewModelScope.launch { repository.update { it.copy(textScale = scale) } }
    }

    fun setLaunchDestination(destination: AppLaunchDestination) {
        viewModelScope.launch { repository.update { it.copy(launchDestination = destination) } }
    }

    fun setPreviewPlayerSize(size: PreviewPlayerSize) {
        viewModelScope.launch { iptvAppSettingsRepository.update { it.copy(previewPlayerSize = size) } }
    }
}
