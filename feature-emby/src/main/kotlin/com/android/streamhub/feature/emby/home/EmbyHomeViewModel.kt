package com.android.streamhub.feature.emby.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType
import com.android.streamhub.feature.emby.data.EmbyLibraryInfo
import com.android.streamhub.feature.emby.data.EmbyLibraryType
import com.android.streamhub.feature.emby.data.EmbySourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Home is a discovery surface, not a full browse (that's what EmbyLibraryScreen's paginated grid
// is for) - and this pass has no See All screen to route an expand action to (see the plan's
// locked-in MVP scope), so every row is just a fixed-length slice rather than paginated. Matches
// JellyfinHomeViewModel's own per-row default limit.
private const val ROW_LIMIT = 20

/**
 * One library's "Latest" shelf - keeps the source [EmbyLibraryInfo] alongside its items (not just
 * the id) since the row's own heading needs the library's name to render and its id/type to route
 * into EmbyLibraryScreen when tapped.
 */
data class EmbyLatestSection(
    val library: EmbyLibraryInfo,
    val items: List<EmbyItemInfo>,
)

data class EmbyHomeUiState(
    // Defaults true so the first frame shows the loading spinner rather than a flash of the
    // "sign in" prompt before the DataStore read resolves - same reasoning as
    // JellyfinHomeViewModel/LiveTvUiState.
    val hasSource: Boolean = true,
    val isLoading: Boolean = true,
    val continueWatching: List<EmbyItemInfo> = emptyList(),
    val nextUp: List<EmbyItemInfo> = emptyList(),
    val latestSections: List<EmbyLatestSection> = emptyList(),
    val errorMessage: String? = null,
) {
    /** Signed in, done loading, and the server genuinely has nothing to show - distinct from [isLoading] so the screen can render a dedicated empty state instead of an indefinite spinner or a blank list. */
    val isEmpty: Boolean
        get() = !isLoading && continueWatching.isEmpty() && nextUp.isEmpty() && latestSections.isEmpty()
}

@HiltViewModel
class EmbyHomeViewModel @Inject constructor(
    private val configRepository: EmbySourceConfigRepository,
    private val browseRepository: EmbyBrowseRepository,
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
     * current state at the cost of a spinner each time rather than an instant stale-then-fresh paint.
     */
    fun loadHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val libraries = browseRepository.getLibraries()
                val continueWatching = browseRepository.getResumeItems(limit = ROW_LIMIT)
                val nextUp = browseRepository.getNextUp(limit = ROW_LIMIT)
                val latestSections = libraries.map { library ->
                    EmbyLatestSection(library = library, items = browseRepository.getLatestMedia(library.id, limit = ROW_LIMIT))
                }
                Triple(continueWatching, nextUp, latestSections)
            }.onSuccess { (continueWatching, nextUp, latestSections) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        continueWatching = continueWatching,
                        nextUp = nextUp,
                        latestSections = latestSections.filter { section -> section.items.isNotEmpty() },
                    )
                }
            }.onFailure { e ->
                // Only surface the error if there's nothing already on screen - a background
                // refresh failing (a transient network blip, most likely) shouldn't rip away
                // content the user can already see from an earlier successful load this session.
                _uiState.update {
                    if (it.continueWatching.isEmpty() && it.nextUp.isEmpty() && it.latestSections.isEmpty()) {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load Emby home")
                    } else {
                        it.copy(isLoading = false)
                    }
                }
            }
        }
    }
}

// A Movies library's items() call only ever wants "Movie", a TV Shows library only ever wants
// "Series" - mirrors EmbyBrowseRepository.getItems' own itemType-to-kind switch. Internal (not
// private) since both EmbyHomeScreen and EmbyHomeScreenTv need it to build the onOpenLibrary
// callback's itemType argument from a tapped library-section heading.
internal fun EmbyLibraryType.toItemType(): EmbyItemType = when (this) {
    EmbyLibraryType.MOVIES -> EmbyItemType.MOVIE
    EmbyLibraryType.TV_SHOWS -> EmbyItemType.SERIES
}
