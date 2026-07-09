package com.android.streamhub.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.design.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUiSettingsViewModel @Inject constructor(
    private val repository: AppUiSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AppUiSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.update { it.copy(themeMode = mode) } }
    }

    fun setTextScale(scale: TextScale) {
        viewModelScope.launch { repository.update { it.copy(textScale = scale) } }
    }
}
