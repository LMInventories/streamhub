package com.android.streamhub.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    jellyfinConfigRepository: JellyfinSourceConfigRepository,
) : ViewModel() {

    val dashboardEntries: StateFlow<List<DashboardEntry>> = jellyfinConfigRepository.configFlow
        .map { config ->
            // Falls back to the server URL rather than "Not connected" when signed in but
            // serverName is unknown (a config saved before that field existed, or the
            // best-effort fetch failed at sign-in time) - still connected, just without a
            // friendly name to show for it.
            val subtitle = config?.let { it.serverName ?: it.serverUrl }
            buildDashboardEntries(jellyfinServerName = subtitle)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildDashboardEntries(jellyfinServerName = null))
}
