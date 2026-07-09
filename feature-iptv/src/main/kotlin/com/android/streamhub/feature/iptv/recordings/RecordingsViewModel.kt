package com.android.streamhub.feature.iptv.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.scheduled.RecordedItemEntity
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledEventsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingsUiState(
    val isLoading: Boolean = true,
    val recordings: List<RecordedItemEntity> = emptyList(),
)

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val repository: ScheduledEventsRepository,
) : ViewModel() {

    val uiState: StateFlow<RecordingsUiState> = repository.observeRecordedItems()
        .map { RecordingsUiState(isLoading = false, recordings = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsUiState())

    fun deleteRecording(item: RecordedItemEntity) {
        viewModelScope.launch { repository.removeRecordedItem(item) }
    }
}
