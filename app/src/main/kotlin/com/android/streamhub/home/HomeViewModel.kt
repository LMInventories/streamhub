package com.android.streamhub.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.jellyfin.data.JellyfinSourceConfigRepository
import com.android.streamhub.update.AppUpdateInfo
import com.android.streamhub.update.AppUpdateInstaller
import com.android.streamhub.update.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    jellyfinConfigRepository: JellyfinSourceConfigRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val appUpdateInstaller: AppUpdateInstaller,
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

    val updateInfo: StateFlow<AppUpdateInfo?> = appUpdateRepository.updateAvailable

    fun canInstallPackages(): Boolean = appUpdateInstaller.canInstallPackages()

    fun requestInstallPermissionIntent(): Intent = appUpdateInstaller.requestInstallPermissionIntent()

    fun startUpdateDownload() {
        val info = updateInfo.value ?: return
        appUpdateInstaller.startDownload(info)
    }

    fun dismissUpdate() {
        val info = updateInfo.value ?: return
        viewModelScope.launch { appUpdateRepository.dismiss(info.versionCode) }
    }
}
