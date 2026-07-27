package com.android.streamhub.feature.iptv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.ChannelSortOrder
import com.android.streamhub.feature.iptv.data.IptvAppSettings
import com.android.streamhub.feature.iptv.data.IptvAppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IptvPlaybackSettingsViewModel @Inject constructor(
    private val repository: IptvAppSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<IptvAppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IptvAppSettings())

    fun setResumeLastChannel(resume: Boolean) {
        viewModelScope.launch { repository.update { it.copy(resumeLastChannel = resume) } }
    }

    fun setChannelSortOrder(order: ChannelSortOrder) {
        viewModelScope.launch { repository.update { it.copy(channelSortOrder = order) } }
    }

    fun setUse24HourTime(use24Hour: Boolean) {
        viewModelScope.launch { repository.update { it.copy(use24HourTime = use24Hour) } }
    }
}
