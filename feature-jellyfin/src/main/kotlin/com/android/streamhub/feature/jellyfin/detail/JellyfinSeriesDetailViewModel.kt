package com.android.streamhub.feature.jellyfin.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val errorMessage: String? = null,
)

@HiltViewModel
class JellyfinSeriesDetailViewModel @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    private val _uiState = MutableStateFlow(JellyfinSeriesDetailUiState())
    val uiState: StateFlow<JellyfinSeriesDetailUiState> = _uiState

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
                Triple(series, seasons, episodesBySeason) to similarShows
            }.onSuccess { (seriesData, similarShows) ->
                val (series, seasons, episodesBySeason) = seriesData
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        series = series,
                        seasons = seasons,
                        episodesBySeasonNumber = episodesBySeason,
                        similarShows = similarShows,
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
}
