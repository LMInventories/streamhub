package com.android.streamhub.feature.jellyfin.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.tmdb.PersonLookupState
import com.android.streamhub.core.tmdb.TmdbRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JellyfinSeriesDetailUiState(
    val isLoading: Boolean = true,
    val series: JellyfinItemInfo? = null,
    val seasons: List<JellyfinItemInfo> = emptyList(),
    val episodesBySeasonNumber: Map<Int, List<JellyfinItemInfo>> = emptyMap(),
    val similarShows: List<JellyfinItemInfo> = emptyList(),
    // Null once the whole series is caught up (or nothing's ever been started) - "Next Up" section
    // is hidden entirely in that case rather than shown empty.
    val nextUpEpisode: JellyfinItemInfo? = null,
    val errorMessage: String? = null,
)

private data class LoadedSeries(
    val series: JellyfinItemInfo?,
    val seasons: List<JellyfinItemInfo>,
    val episodesBySeasonNumber: Map<Int, List<JellyfinItemInfo>>,
    val similarShows: List<JellyfinItemInfo>,
    val nextUpEpisode: JellyfinItemInfo?,
)

@HiltViewModel
class JellyfinSeriesDetailViewModel @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    private val tmdbRepository: TmdbRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    private val _uiState = MutableStateFlow(JellyfinSeriesDetailUiState())
    val uiState: StateFlow<JellyfinSeriesDetailUiState> = _uiState

    private val _personLookupState = MutableStateFlow<PersonLookupState>(PersonLookupState.Idle)
    val personLookupState: StateFlow<PersonLookupState> = _personLookupState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val series = browseRepository.getItem(seriesId)
                val seasons = browseRepository.getSeasons(seriesId)
                // Sequential rather than parallel - a show's season count is small enough
                // (single digits, almost always) that this isn't worth the extra complexity of
                // fanning the calls out concurrently.
                val episodesBySeason = seasons.associate { season ->
                    (season.indexNumber ?: 0) to browseRepository.getEpisodes(seriesId, season.id)
                }
                val similarShows = runCatching { browseRepository.getSimilarShows(seriesId) }.getOrDefault(emptyList())
                val nextUpEpisode = runCatching { browseRepository.getNextUp(seriesId) }.getOrNull()
                LoadedSeries(series, seasons, episodesBySeason, similarShows, nextUpEpisode)
            }.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        series = loaded.series,
                        seasons = loaded.seasons,
                        episodesBySeasonNumber = loaded.episodesBySeasonNumber,
                        similarShows = loaded.similarShows,
                        nextUpEpisode = loaded.nextUpEpisode,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load series") }
            }
        }
    }

    fun toggleFavorite() {
        val series = _uiState.value.series ?: return
        val optimistic = !series.isFavorite
        _uiState.update { it.copy(series = it.series?.copy(isFavorite = optimistic)) }
        viewModelScope.launch {
            val confirmed = browseRepository.toggleFavorite(series.id, series.isFavorite)
            _uiState.update { it.copy(series = it.series?.copy(isFavorite = confirmed ?: series.isFavorite)) }
        }
    }

    /** See JellyfinItemDetailViewModel.onPersonClick's matching doc - identical shape. */
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
