package com.android.streamhub.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvBrowseRepository
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import com.android.streamhub.feature.iptv.data.IptvVodRepository
import com.android.streamhub.feature.iptv.data.VodMovieInfo
import com.android.streamhub.feature.iptv.data.VodShowInfo
import com.android.streamhub.feature.iptv.data.epg.EpgGridRepository
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledEventsRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An EPG search hit needs both the program and the channel it airs on - scheduleRecording/scheduleReminder take both. */
data class EpgSearchResult(val channel: IptvChannelInfo, val program: EpgProgram)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val epgResults: List<EpgSearchResult> = emptyList(),
    val vodMovies: List<VodMovieInfo> = emptyList(),
    val vodShows: List<VodShowInfo> = emptyList(),
    val jellyfinResults: List<JellyfinItemInfo> = emptyList(),
) {
    val isEmpty: Boolean get() = epgResults.isEmpty() && vodMovies.isEmpty() && vodShows.isEmpty() && jellyfinResults.isEmpty()
}

private const val SEARCH_DEBOUNCE_MS = 400L

/**
 * Fans a single query out across every registered source concurrently - Live TV/EPG and Xtream
 * VOD (via IptvBrowseRepository/IptvVodRepository/EpgGridRepository, all client-side filtered -
 * neither Xtream nor XMLTV has a server-side search endpoint) and Jellyfin (via
 * JellyfinBrowseRepository.search(), genuine server-side search). Emby has no repository yet
 * (feature-emby doesn't exist), so its section is simply absent from the result rather than
 * modeled as a permanent empty state - SearchScreen shows a "coming soon" row for it instead.
 *
 * Deliberately lives in :app rather than any single feature module - it depends on both
 * feature-iptv and feature-jellyfin, and feature modules never depend on each other, the same
 * reasoning SettingsScreen/HomeScreenPhone already follow for cross-source aggregation.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val iptvBrowseRepository: IptvBrowseRepository,
    private val iptvVodRepository: IptvVodRepository,
    private val epgGridRepository: EpgGridRepository,
    private val jellyfinBrowseRepository: JellyfinBrowseRepository,
    private val scheduledEventsRepository: ScheduledEventsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collectLatest { query ->
                if (query.isBlank()) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            hasSearched = false,
                            epgResults = emptyList(),
                            vodMovies = emptyList(),
                            vodShows = emptyList(),
                            jellyfinResults = emptyList(),
                        )
                    }
                    return@collectLatest
                }
                _uiState.update { it.copy(isSearching = true) }
                runSearch(query)
            }
        }
    }

    private suspend fun runSearch(query: String) = coroutineScope {
        // Each source fails/succeeds independently - one source being unreachable (e.g. no
        // Jellyfin account signed in, so apiOrNull() returns null and search() already returns
        // emptyList() for that) should never blank out results the others did find.
        val epgDeferred = async {
            runCatching {
                val channels = iptvBrowseRepository.getAllChannels()
                epgGridRepository.searchUpcoming(channels, query).map { (channel, program) -> EpgSearchResult(channel, program) }
            }.getOrDefault(emptyList())
        }
        val moviesDeferred = async { runCatching { iptvVodRepository.searchMovies(query) }.getOrDefault(emptyList()) }
        val showsDeferred = async { runCatching { iptvVodRepository.searchShows(query) }.getOrDefault(emptyList()) }
        val jellyfinDeferred = async { runCatching { jellyfinBrowseRepository.search(query) }.getOrDefault(emptyList()) }

        val epg = epgDeferred.await()
        val movies = moviesDeferred.await()
        val shows = showsDeferred.await()
        val jellyfin = jellyfinDeferred.await()

        // The query could have changed again while these were in flight - collectLatest already
        // cancels the stale coroutine, but this guards the (already-cancelled) update too.
        if (queryFlow.value != query) return@coroutineScope
        _uiState.update {
            it.copy(
                isSearching = false,
                hasSearched = true,
                epgResults = epg,
                vodMovies = movies,
                vodShows = shows,
                jellyfinResults = jellyfin,
            )
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun scheduleRecording(channel: IptvChannelInfo, program: EpgProgram, startAdjustMinutes: Int, endAdjustMinutes: Int) {
        viewModelScope.launch { scheduledEventsRepository.addRecording(channel, program, startAdjustMinutes, endAdjustMinutes) }
    }

    fun scheduleReminder(channel: IptvChannelInfo, program: EpgProgram, leadMinutes: Int) {
        viewModelScope.launch { scheduledEventsRepository.addReminder(channel, program, leadMinutes) }
    }
}
