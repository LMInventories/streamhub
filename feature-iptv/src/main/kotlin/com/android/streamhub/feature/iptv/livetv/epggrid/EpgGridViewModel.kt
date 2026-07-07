package com.android.streamhub.feature.iptv.livetv.epggrid

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvBrowseRepository
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import com.android.streamhub.feature.iptv.data.epg.EpgGridRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val GRID_DAYS = 7L

data class EpgGridUiState(
    val isLoading: Boolean = true,
    val channels: List<IptvChannelInfo> = emptyList(),
    val programmesByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val windowStart: Instant = Instant.now().truncatedTo(ChronoUnit.HOURS),
    val windowEnd: Instant = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(GRID_DAYS, ChronoUnit.DAYS),
    val errorMessage: String? = null,
)

@HiltViewModel
class EpgGridViewModel @Inject constructor(
    private val browseRepository: IptvBrowseRepository,
    private val epgGridRepository: EpgGridRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: String = Route.decodeCategoryId(
        checkNotNull(savedStateHandle["categoryId"]) { "categoryId is required" },
    )

    private val _uiState = MutableStateFlow(EpgGridUiState())
    val uiState: StateFlow<EpgGridUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val channels = browseRepository.getChannels(categoryId)
                val hasEpg = epgGridRepository.ensureFresh()
                val windowStart = Instant.now().truncatedTo(ChronoUnit.HOURS)
                val windowEnd = windowStart.plus(GRID_DAYS, ChronoUnit.DAYS)
                val grid = if (hasEpg) {
                    epgGridRepository.getGrid(channels.map { it.id }, windowStart, windowEnd)
                } else {
                    emptyMap()
                }
                Triple(channels, grid, windowStart to windowEnd)
            }.onSuccess { (channels, grid, window) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        channels = channels,
                        programmesByChannel = grid,
                        windowStart = window.first,
                        windowEnd = window.second,
                        errorMessage = if (grid.isEmpty()) "No EPG guide available for this source" else null,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load guide") }
            }
        }
    }
}
