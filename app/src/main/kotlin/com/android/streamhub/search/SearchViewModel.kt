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
    // True once the Live TV tab has been expanded past DEFAULT_RESULT_LIMIT for the current
    // query - drives both "has this already been expanded" (so the expand button doesn't fire
    // twice) and the screen's "results size == the limit that produced them, so there might be
    // more" heuristic for whether to show the expand affordance at all.
    val epgExpanded: Boolean = false,
    val vodMovies: List<VodMovieInfo> = emptyList(),
    val vodShows: List<VodShowInfo> = emptyList(),
    val vodExpanded: Boolean = false,
    val jellyfinResults: List<JellyfinItemInfo> = emptyList(),
) {
    val isEmpty: Boolean get() = epgResults.isEmpty() && vodMovies.isEmpty() && vodShows.isEmpty() && jellyfinResults.isEmpty()
}

private const val SEARCH_DEBOUNCE_MS = 400L

/** Search's default per-tab cap - Live TV/VOD results beyond this need an explicit "Show more" tap, since an unbounded fuzzy match over a large guide/catalog can otherwise return far more than is useful to scroll through. */
const val DEFAULT_SEARCH_RESULT_LIMIT = 30
private const val EXPANDED_SEARCH_RESULT_LIMIT = 200

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
                            epgExpanded = false,
                            vodMovies = emptyList(),
                            vodShows = emptyList(),
                            vodExpanded = false,
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
        // emptyList() for that) should never blank out results the others did find. A fresh query
        // always starts capped at the default limit - any earlier expand() was for the old query.
        val epgDeferred = async { fetchEpgResults(query, DEFAULT_SEARCH_RESULT_LIMIT) }
        val moviesDeferred = async { runCatching { iptvVodRepository.searchMovies(query, DEFAULT_SEARCH_RESULT_LIMIT) }.getOrDefault(emptyList()) }
        val showsDeferred = async { runCatching { iptvVodRepository.searchShows(query, DEFAULT_SEARCH_RESULT_LIMIT) }.getOrDefault(emptyList()) }
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
                epgExpanded = false,
                vodMovies = movies,
                vodShows = shows,
                vodExpanded = false,
                jellyfinResults = jellyfin,
            )
        }
    }

    /**
     * searchUpcoming() only ever reads EpgGridRepository's Room cache - it never fetches. Every
     * other caller of that cache (LiveTvViewModel) fetches it on its own init, so a search run
     * before Live TV was ever opened this session (or with a stale/empty cache) used to silently
     * return zero EPG results with no indication why. ensureFresh() is cheap to call unconditionally
     * here - it no-ops immediately if the cache is already fresh (see its own doc), and only
     * actually downloads the guide on a genuinely stale/empty cache.
     */
    private suspend fun fetchEpgResults(query: String, limit: Int): List<EpgSearchResult> = runCatching {
        runCatching { epgGridRepository.ensureFresh() }
        val channels = iptvBrowseRepository.getAllChannels()
        epgGridRepository.searchUpcoming(channels, query, limit).map { (channel, program) -> EpgSearchResult(channel, program) }
    }.getOrDefault(emptyList())

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    /** Re-fetches Live TV results for the current query at a much higher cap - cheap, since the guide is already loaded locally (Room + in-memory channel cache), not a network round-trip. */
    fun expandLiveTvResults() {
        val query = _uiState.value.query
        if (_uiState.value.epgExpanded || query.isBlank()) return
        _uiState.update { it.copy(epgExpanded = true) }
        viewModelScope.launch {
            val epg = fetchEpgResults(query, EXPANDED_SEARCH_RESULT_LIMIT)
            if (queryFlow.value == query) _uiState.update { it.copy(epgResults = epg) }
        }
    }

    /** Re-fetches VOD results for the current query at a much higher cap - cheap, since categories/items are already cached in-memory by IptvVodRepository after the first fetch. */
    fun expandVodResults() {
        val query = _uiState.value.query
        if (_uiState.value.vodExpanded || query.isBlank()) return
        _uiState.update { it.copy(vodExpanded = true) }
        viewModelScope.launch {
            val movies = runCatching { iptvVodRepository.searchMovies(query, EXPANDED_SEARCH_RESULT_LIMIT) }.getOrDefault(emptyList())
            val shows = runCatching { iptvVodRepository.searchShows(query, EXPANDED_SEARCH_RESULT_LIMIT) }.getOrDefault(emptyList())
            if (queryFlow.value == query) _uiState.update { it.copy(vodMovies = movies, vodShows = shows) }
        }
    }

    fun scheduleRecording(channel: IptvChannelInfo, program: EpgProgram, startAdjustMinutes: Int, endAdjustMinutes: Int) {
        viewModelScope.launch { scheduledEventsRepository.addRecording(channel, program, startAdjustMinutes, endAdjustMinutes) }
    }

    fun scheduleReminder(channel: IptvChannelInfo, program: EpgProgram, leadMinutes: Int) {
        viewModelScope.launch { scheduledEventsRepository.addReminder(channel, program, leadMinutes) }
    }
}
