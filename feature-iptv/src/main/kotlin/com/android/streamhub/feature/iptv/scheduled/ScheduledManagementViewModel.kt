package com.android.streamhub.feature.iptv.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledEventsRepository
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledRecordingEntity
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledReminderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduledManagementUiState(
    val isLoading: Boolean = true,
    val reminders: List<ScheduledReminderEntity> = emptyList(),
    val recordings: List<ScheduledRecordingEntity> = emptyList(),
)

/**
 * Backs the "Scheduled" settings screen - reminders and pending (not-yet-captured) recordings
 * share one screen since they're both "things the user asked to happen later that they might
 * want to cancel," and ScheduledEventsRepository already tracks both with the exact same shape
 * (observe + remove), unlike RecordedItemEntity (already-captured recordings, which is what the
 * separate Recordings library screen shows instead).
 */
@HiltViewModel
class ScheduledManagementViewModel @Inject constructor(
    private val repository: ScheduledEventsRepository,
) : ViewModel() {
    val uiState: StateFlow<ScheduledManagementUiState> = combine(
        repository.observeReminders(),
        repository.observeRecordings(),
    ) { reminders, recordings ->
        ScheduledManagementUiState(isLoading = false, reminders = reminders, recordings = recordings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduledManagementUiState())

    fun cancelReminder(reminder: ScheduledReminderEntity) {
        viewModelScope.launch { repository.removeReminder(reminder) }
    }

    fun cancelRecording(recording: ScheduledRecordingEntity) {
        viewModelScope.launch { repository.removeRecording(recording) }
    }
}
