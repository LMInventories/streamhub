package com.android.streamhub.feature.jellyfin.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JellyfinHomeUiState(
    // Defaults true so the first frame shows the loading spinner rather than a flash of the
    // "sign in" prompt before the DataStore read resolves - same reasoning as LiveTvUiState.
    val hasSource: Boolean = true,
    val isLoading: Boolean = true,
    val libraries: List<JellyfinLibraryInfo> = emptyList(),
    val continueWatching: List<JellyfinItemInfo> = emptyList(),
    val nextUp: List<JellyfinItemInfo> = emptyList(),
    val latestByLibrary: Map<String, List<JellyfinItemInfo>> = emptyMap(),
    val errorMessage: String? = null,
)

@HiltViewModel
class JellyfinHomeViewModel @Inject constructor(
    private val configRepository: JellyfinSourceConfigRepository,
    private val browseRepository: JellyfinBrowseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyfinHomeUiState())
    val uiState: StateFlow<JellyfinHomeUiState> = _uiState

    init {
        viewModelScope.launch {
            configRepository.configFlow.collect { config ->
                val hasSource = config != null
                _uiState.update { it.copy(hasSource = hasSource) }
                if (hasSource) loadHome()
            }
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val libraries = browseRepository.getLibraries()
                val continueWatching = browseRepository.getResumeItems()
                val nextUp = browseRepository.getNextUp()
                val latestByLibrary = libraries.associate { library -> library.id to browseRepository.getLatestMedia(library.id) }
                Triple(libraries, continueWatching, nextUp to latestByLibrary)
            }.onSuccess { (libraries, continueWatching, nextUpAndLatest) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        libraries = libraries,
                        continueWatching = continueWatching,
                        nextUp = nextUpAndLatest.first,
                        latestByLibrary = nextUpAndLatest.second,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load Jellyfin home") }
            }
        }
    }
}
