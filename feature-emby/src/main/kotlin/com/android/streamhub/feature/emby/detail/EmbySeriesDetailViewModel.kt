package com.android.streamhub.feature.emby.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.tmdb.PersonLookupState
import com.android.streamhub.core.tmdb.TmdbRepository
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyCastMember
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmbySeriesDetailUiState(
    val isLoading: Boolean = true,
    val series: EmbyItemInfo? = null,
    val seasons: List<EmbyItemInfo> = emptyList(),
    val episodesBySeasonNumber: Map<Int, List<EmbyItemInfo>> = emptyMap(),
    // Null once the whole series is caught up (or nothing's ever been started) - the "Continue"
    // section is hidden entirely in that case rather than shown empty. Mirrors
    // JellyfinSeriesDetailUiState.nextUpEpisode exactly, minus that file's similarShows field
    // ("More Like This" is out of scope this pass per the approved plan).
    val nextUpEpisode: EmbyItemInfo? = null,
    val errorMessage: String? = null,
    // See EmbyItemDetailUiState.castImageFallbacks' own doc - identical shape, just keyed off the
    // series' own cast list instead of a single item's.
    val castImageFallbacks: Map<String, String> = emptyMap(),
)

private data class LoadedSeries(
    val series: EmbyItemInfo?,
    val seasons: List<EmbyItemInfo>,
    val episodesBySeasonNumber: Map<Int, List<EmbyItemInfo>>,
    val nextUpEpisode: EmbyItemInfo?,
)

/**
 * Loads a series, its seasons, each season's episode list, and the series' own Next Up episode
 * (surfaced as a "Continue" affordance) - same shape as JellyfinSeriesDetailViewModel, minus that
 * file's "similar shows" row (out of scope this pass) and favorite toggle (EmbyItemInfo has no
 * isFavorite field - no favorite/watched state at all yet).
 */
@HiltViewModel
class EmbySeriesDetailViewModel @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
    private val tmdbRepository: TmdbRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    private val _uiState = MutableStateFlow(EmbySeriesDetailUiState())
    val uiState: StateFlow<EmbySeriesDetailUiState> = _uiState

    private val _personLookupState = MutableStateFlow<PersonLookupState>(PersonLookupState.Idle)
    val personLookupState: StateFlow<PersonLookupState> = _personLookupState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val series = browseRepository.getItem(seriesId)
                val seasons = browseRepository.getSeasons(seriesId)
                // Sequential rather than parallel - same reasoning as
                // JellyfinSeriesDetailViewModel: a show's season count is small enough (single
                // digits, almost always) that fanning these out concurrently isn't worth the
                // extra complexity.
                val episodesBySeason = seasons.associate { season ->
                    (season.indexNumber ?: 0) to browseRepository.getEpisodes(seriesId, season.id)
                }
                val nextUpEpisode = runCatching { browseRepository.getNextUp(seriesId) }.getOrNull()
                LoadedSeries(series, seasons, episodesBySeason, nextUpEpisode)
            }.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        series = loaded.series,
                        seasons = loaded.seasons,
                        episodesBySeasonNumber = loaded.episodesBySeasonNumber,
                        nextUpEpisode = loaded.nextUpEpisode,
                    )
                }
                loaded.series?.let { resolveCastImageFallbacks(it.cast) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load series") }
            }
        }
    }

    /** See TmdbRepository.findProfileImageFallbacks's own doc. Fire-and-forget past series load - merges into uiState whenever it resolves rather than blocking isLoading on a TMDB round trip. */
    private fun resolveCastImageFallbacks(members: List<EmbyCastMember>) {
        val missing = members.filter { it.imageUrl == null }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val fallbacks = tmdbRepository.findProfileImageFallbacks(missing.map { it.id to it.name })
            if (fallbacks.isNotEmpty()) _uiState.update { it.copy(castImageFallbacks = it.castImageFallbacks + fallbacks) }
        }
    }

    /** See JellyfinItemDetailViewModel.onPersonClick's matching doc - identical shape, both sources share TmdbRepository unchanged. */
    fun onPersonClick(name: String) {
        _personLookupState.value = PersonLookupState.Loading
        viewModelScope.launch {
            val person = tmdbRepository.findPerson(name)
            _personLookupState.value = person?.let { PersonLookupState.Found(it.id) } ?: PersonLookupState.NotFound
        }
    }

    fun consumePersonLookup() {
        _personLookupState.value = PersonLookupState.Idle
    }
}
