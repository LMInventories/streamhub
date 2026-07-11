package com.android.streamhub.feature.jellyfin.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinAppSettingsRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeCacheRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSection
import com.android.streamhub.feature.jellyfin.data.JellyfinHomeSectionKeys
import com.android.streamhub.feature.jellyfin.data.JellyfinLibraryInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JellyfinHomeUiState(
    // Defaults true so the first frame shows the loading spinner rather than a flash of the
    // "sign in" prompt before the DataStore read resolves - same reasoning as LiveTvUiState.
    val hasSource: Boolean = true,
    val isLoading: Boolean = true,
    val libraries: List<JellyfinLibraryInfo> = emptyList(),
    val sections: List<JellyfinHomeSection> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class JellyfinHomeViewModel @Inject constructor(
    private val configRepository: JellyfinSourceConfigRepository,
    private val browseRepository: JellyfinBrowseRepository,
    private val appSettingsRepository: JellyfinAppSettingsRepository,
    private val homeCacheRepository: JellyfinHomeCacheRepository,
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
            // Stale-while-revalidate: a Hilt Singleton repository's in-memory cache (the pattern
            // IptvBrowseRepository/IptvVodRepository use) doesn't survive process death, so every
            // cold app start used to sit on the loading spinner for the full network round-trip
            // before showing anything. If there's a persisted cache from a previous session, show
            // it immediately instead - the fetch below still runs regardless and replaces it with
            // fresh data (picking up anything genuinely new/changed) the moment it completes.
            if (_uiState.value.sections.isEmpty()) {
                val cached = homeCacheRepository.readCache()
                if (cached != null) {
                    _uiState.update { it.copy(isLoading = false, sections = cached) }
                } else {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
            _uiState.update { it.copy(errorMessage = null) }
            runCatching {
                val libraries = browseRepository.getLibraries()
                val byKey = buildMap {
                    put(
                        JellyfinHomeSectionKeys.CONTINUE_WATCHING,
                        JellyfinHomeSection(JellyfinHomeSectionKeys.CONTINUE_WATCHING, "Continue Watching", browseRepository.getResumeItems(), hasSeeAll = true),
                    )
                    put(
                        JellyfinHomeSectionKeys.NEXT_UP,
                        JellyfinHomeSection(JellyfinHomeSectionKeys.NEXT_UP, "Next Up", browseRepository.getNextUp(), hasSeeAll = true),
                    )
                    put(
                        JellyfinHomeSectionKeys.FAVOURITES,
                        JellyfinHomeSection(JellyfinHomeSectionKeys.FAVOURITES, "Favourites", browseRepository.getFavorites(startIndex = 0, limit = 20), hasSeeAll = true),
                    )
                    libraries.forEach { library ->
                        val key = JellyfinHomeSectionKeys.library(library.id)
                        put(key, JellyfinHomeSection(key, "Latest in ${library.name}", browseRepository.getLatestMedia(library.id), hasSeeAll = true))
                    }
                }
                libraries to byKey
            }.onSuccess { (libraries, byKey) ->
                val savedOrder = appSettingsRepository.settingsFlow.first().homeSectionOrder
                // Saved order first (skipping keys that no longer resolve to anything, e.g. a
                // library that got deleted server-side), then anything not yet in the saved order
                // (a newly added library, or the saved order simply being empty/default) appended
                // in its natural discovery order rather than dropped.
                val ordered = savedOrder.mapNotNull { byKey[it] } +
                    byKey.values.filter { it.key !in savedOrder }
                val sections = ordered.filter { section -> section.items.isNotEmpty() }
                _uiState.update { it.copy(isLoading = false, libraries = libraries, sections = sections) }
                homeCacheRepository.writeCache(sections)
            }.onFailure { e ->
                // Only surface the error if there's nothing already on screen - a background
                // refresh failing (a transient network blip, most likely) shouldn't rip away
                // content the user can already see, whether that came from the persisted cache
                // above or an earlier successful load this session.
                _uiState.update {
                    if (it.sections.isEmpty()) it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load Jellyfin home")
                    else it.copy(isLoading = false)
                }
            }
        }
    }
}
