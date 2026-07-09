package com.android.streamhub.feature.jellyfin.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinAppSettingsRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JellyfinLibraryVisibilityUiState(
    val isLoading: Boolean = true,
    val libraries: List<JellyfinLibraryInfo> = emptyList(),
    val hiddenLibraryIds: Set<String> = emptySet(),
)

@HiltViewModel
class JellyfinLibraryVisibilityViewModel @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    private val appSettingsRepository: JellyfinAppSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinLibraryVisibilityUiState())
    val uiState: StateFlow<JellyfinLibraryVisibilityUiState> = _uiState

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
