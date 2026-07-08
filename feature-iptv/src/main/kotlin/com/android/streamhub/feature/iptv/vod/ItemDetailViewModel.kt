package com.android.streamhub.feature.iptv.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.domain.WatchProgressRepository
import com.android.streamhub.feature.iptv.data.IptvVodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val posterUrl: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val audioTag: String? = null,
    val videoTag: String? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    // null = start from the beginning (show "Play"); non-null = resumable (show "Resume").
    val resumeFractionComplete: Float? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val vodRepository: IptvVodRepository,
    private val watchProgressRepository: WatchProgressRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"]) { "itemId is required" }
    private val isEpisode: Boolean = itemId.startsWith("episode:")

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            runCatching {
                if (isEpisode) vodRepository.getEpisodeDetail(itemId) else vodRepository.getMovieDetail(itemId)
            }
                .onSuccess { detail ->
                    if (detail == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Not found") }
                        return@onSuccess
                    }
                    val progress = watchProgressRepository.getProgress(SourceType.IPTV, itemId)
                    val resumeFraction = if (progress != null && !progress.isNearlyComplete) progress.fractionComplete else null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = detail.title,
                            posterUrl = detail.posterUrl,
                            plot = detail.plot,
                            cast = detail.cast,
                            director = detail.director,
                            genre = detail.genre,
                            releaseDate = detail.releaseDate,
                            rating = detail.rating,
                            duration = detail.duration,
                            audioTag = detail.audioTag,
                            videoTag = detail.videoTag,
                            seriesName = detail.seriesName,
                            seasonNumber = detail.seasonNumber,
                            episodeNumber = detail.episodeNumber,
                            resumeFractionComplete = resumeFraction,
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load details") } }
        }
    }
}
