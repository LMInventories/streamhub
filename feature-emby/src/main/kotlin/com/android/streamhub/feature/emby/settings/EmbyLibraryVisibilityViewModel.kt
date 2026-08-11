package com.android.streamhub.feature.emby.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyAppSettingsRepository
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyLibraryInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmbyLibraryVisibilityUiState(
    val isLoading: Boolean = true,
    val libraries: List<EmbyLibraryInfo> = emptyList(),
    val hiddenLibraryIds: Set<String> = emptySet(),
)

@HiltViewModel
class EmbyLibraryVisibilityViewModel @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
    private val appSettingsRepository: EmbyAppSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmbyLibraryVisibilityUiState())
    val uiState: StateFlow<EmbyLibraryVisibilityUiState> = _uiState

    init {
        viewModelScope.launch {
            // includeAppHidden=true - this screen is the one place a library the user already hid
            // still needs to be listed, so they can un-hide it again.
            val libraries = browseRepository.getLibraries(includeAppHidden = true)
            appSettingsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(isLoading = false, libraries = libraries, hiddenLibraryIds = settings.hiddenLibraryIds) }
            }
        }
    }

    fun toggleVisible(libraryId: String) {
        viewModelScope.launch {
            appSettingsRepository.update { settings ->
                val hidden = settings.hiddenLibraryIds
                settings.copy(hiddenLibraryIds = if (libraryId in hidden) hidden - libraryId else hidden + libraryId)
            }
        }
    }
}
