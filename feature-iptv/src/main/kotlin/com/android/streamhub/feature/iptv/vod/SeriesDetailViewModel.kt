package com.android.streamhub.feature.iptv.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.IptvVodRepository
import com.android.streamhub.feature.iptv.data.VodEpisodeInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val posterUrl: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    // Season number -> episodes in that season, in display order.
    val episodesBySeason: Map<Int, List<VodEpisodeInfo>> = emptyMap(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val vodRepository: IptvVodRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"]) { "seriesId is required" }

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            runCatching { vodRepository.getSeriesDetail(seriesId) }
                .onSuccess { detail ->
                    if (detail == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Show not found") }
                        return@onSuccess
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = detail.name,
                            posterUrl = detail.posterUrl,
                            plot = detail.plot,
                            cast = detail.cast,
                            genre = detail.genre,
                            rating = detail.rating,
                            episodesBySeason = detail.episodes.groupBy(VodEpisodeInfo::seasonNumber),
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load show") } }
        }
    }
}
