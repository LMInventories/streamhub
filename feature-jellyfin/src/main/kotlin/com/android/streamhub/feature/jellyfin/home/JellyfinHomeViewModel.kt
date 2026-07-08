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
    val favorites: List<JellyfinItemInfo> = emptyList(),
    val latestByLibrary: Map<String, List<JellyfinItemInfo>> = emptyMap(),
    val errorMessage: String? = null,
)

private data class HomeLoadResult(
    val libraries: List<JellyfinLibraryInfo>,
    val continueWatching: List<JellyfinItemInfo>,
    val nextUp: List<JellyfinItemInfo>,
    val favorites: List<JellyfinItemInfo>,
    val latestByLibrary: Map<String, List<JellyfinItemInfo>>,
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
                HomeLoadResult(
                    libraries = libraries,
                    continueWatching = browseRepository.getResumeItems(),
                    nextUp = browseRepository.getNextUp(),
                    favorites = browseRepository.getFavorites(startIndex = 0, limit = 20),
                    latestByLibrary = libraries.associate { library -> library.id to browseRepository.getLatestMedia(library.id) },
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        libraries = result.libraries,
                        continueWatching = result.continueWatching,
                        nextUp = result.nextUp,
                        favorites = result.favorites,
                        latestByLibrary = result.latestByLibrary,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load Jellyfin home") }
            }
        }
    }
}
