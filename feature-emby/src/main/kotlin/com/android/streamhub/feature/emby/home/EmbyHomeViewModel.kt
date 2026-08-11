package com.android.streamhub.feature.emby.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyAppSettingsRepository
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyHomeSection
import com.android.streamhub.feature.emby.data.EmbyHomeSectionKeys
import com.android.streamhub.feature.emby.data.EmbyItemType
import com.android.streamhub.feature.emby.data.EmbyLibraryInfo
import com.android.streamhub.feature.emby.data.EmbyLibraryType
import com.android.streamhub.feature.emby.data.EmbySourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Home is a discovery surface, not a full browse (that's what EmbyLibraryScreen's paginated grid
// is for) - and this pass has no See All screen to route an expand action to for Continue
// Watching/Next Up (see EmbyHomeSection's hasSeeAll usage in EmbyHomeViewModel.loadHome below), so
// every row is just a fixed-length slice rather than paginated. Matches JellyfinHomeViewModel's
// own per-row default limit.
private const val ROW_LIMIT = 20

data class EmbyHomeUiState(
    // Defaults true so the first frame shows the loading spinner rather than a flash of the
    // "sign in" prompt before the DataStore read resolves - same reasoning as
    // JellyfinHomeViewModel/LiveTvUiState.
    val hasSource: Boolean = true,
    val isLoading: Boolean = true,
    val libraries: List<EmbyLibraryInfo> = emptyList(),
    val sections: List<EmbyHomeSection> = emptyList(),
    val errorMessage: String? = null,
) {
    /** Signed in, done loading, and the server genuinely has nothing to show - distinct from [isLoading] so the screen can render a dedicated empty state instead of an indefinite spinner or a blank list. */
    val isEmpty: Boolean
        get() = !isLoading && sections.isEmpty()
}

@HiltViewModel
class EmbyHomeViewModel @Inject constructor(
    private val configRepository: EmbySourceConfigRepository,
    private val browseRepository: EmbyBrowseRepository,
    private val appSettingsRepository: EmbyAppSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmbyHomeUiState())
    val uiState: StateFlow<EmbyHomeUiState> = _uiState

    init {
        viewModelScope.launch {
            configRepository.configFlow.collect { config ->
                val hasSource = config != null
                _uiState.update { it.copy(hasSource = hasSource) }
                if (hasSource) loadHome()
            }
        }
    }

    /**
     * Unlike JellyfinHomeViewModel, there's no home-screen cache layer this pass
     * (JellyfinHomeCacheRepository's equivalent is deliberately out of scope) - every call here is
     * a fresh set of network round-trips, so revisiting this screen always reflects the server's
     * current state at the cost of a spinner each time rather than an instant stale-then-fresh
     * paint.
     */
    fun loadHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val libraries = browseRepository.getLibraries()
                val byKey = buildMap {
                    put(
                        EmbyHomeSectionKeys.CONTINUE_WATCHING,
                        EmbyHomeSection(
                            EmbyHomeSectionKeys.CONTINUE_WATCHING,
                            "Continue Watching",
                            browseRepository.getResumeItems(limit = ROW_LIMIT),
                            // No See-All screen for Continue Watching this pass - unlike
                            // Favourites/each library, there's no real nav target to send this to.
                            hasSeeAll = false,
                        ),
                    )
                    put(
                        EmbyHomeSectionKeys.NEXT_UP,
                        EmbyHomeSection(
                            EmbyHomeSectionKeys.NEXT_UP,
                            "Next Up",
                            browseRepository.getNextUp(limit = ROW_LIMIT),
                            hasSeeAll = false,
                        ),
                    )
                    put(
                        EmbyHomeSectionKeys.FAVOURITES,
                        EmbyHomeSection(
                            EmbyHomeSectionKeys.FAVOURITES,
                            "Favourites",
                            browseRepository.getFavorites(startIndex = 0, limit = ROW_LIMIT),
                            hasSeeAll = true,
                        ),
                    )
                    libraries.forEach { library ->
                        val key = EmbyHomeSectionKeys.library(library.id)
                        put(
                            key,
                            EmbyHomeSection(
                                key,
                                "Latest in ${library.name}",
                                browseRepository.getLatestMedia(library.id, limit = ROW_LIMIT),
                                hasSeeAll = true,
                            ),
                        )
                    }
                }
                libraries to byKey
            }.onSuccess { (libraries, byKey) ->
                val savedOrder = appSettingsRepository.settingsFlow.first().homeSectionOrder
                // Saved order first (skipping keys that no longer resolve to anything, e.g. a
                // library that got deleted server-side), then anything not yet in the saved order
                // (a newly added library, or the saved order simply being empty/default) appended
                // in its natural discovery order rather than dropped. Mirrors
                // JellyfinHomeViewModel.loadHome exactly.
                val ordered = savedOrder.mapNotNull { byKey[it] } +
                    byKey.values.filter { it.key !in savedOrder }
                val sections = ordered.filter { section -> section.items.isNotEmpty() }
                _uiState.update { it.copy(isLoading = false, libraries = libraries, sections = sections) }
            }.onFailure { e ->
                // Only surface the error if there's nothing already on screen - a background
                // refresh failing (a transient network blip, most likely) shouldn't rip away
                // content the user can already see from an earlier successful load this session.
                _uiState.update {
                    if (it.sections.isEmpty()) it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load Emby home")
                    else it.copy(isLoading = false)
                }
            }
        }
    }
}

// A Movies library's items() call only ever wants "Movie", a TV Shows library only ever wants
// "Series" - mirrors EmbyBrowseRepository.getItems' own itemType-to-kind switch. Internal (not
// private) since both EmbyHomeScreen and EmbyHomeScreenTv need it to build the onOpenLibrary
// callback's itemType argument from a tapped library section.
internal fun EmbyLibraryType.toItemType(): EmbyItemType = when (this) {
    EmbyLibraryType.MOVIES -> EmbyItemType.MOVIE
    EmbyLibraryType.TV_SHOWS -> EmbyItemType.SERIES
}
