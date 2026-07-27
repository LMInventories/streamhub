package com.android.streamhub.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.update.AppUpdateInfo
import com.android.streamhub.update.AppUpdateInstaller
import com.android.streamhub.update.AppUpdateRepository
import com.android.streamhub.update.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val appUpdateInstaller: AppUpdateInstaller,
) : ViewModel() {

    val updateInfo: StateFlow<AppUpdateInfo?> = appUpdateRepository.updateAvailable

    private val _isCheckingForUpdate = MutableStateFlow(false)
    val isCheckingForUpdate: StateFlow<Boolean> = _isCheckingForUpdate

    private val _lastCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val lastCheckResult: StateFlow<UpdateCheckResult?> = _lastCheckResult

    fun canInstallPackages(): Boolean = appUpdateInstaller.canInstallPackages()

    fun requestInstallPermissionIntent(): Intent = appUpdateInstaller.requestInstallPermissionIntent()

    fun startUpdateDownload() {
        val info = updateInfo.value ?: return
        appUpdateInstaller.startDownload(info)
    }

    /** Manual "Check for Updates" row tap when no update is already known - bypassDismiss=true so an earlier Home-banner dismissal doesn't hide the result of an explicit check. */
    fun checkForUpdatesNow() {
        if (_isCheckingForUpdate.value) return
        viewModelScope.launch {
            _isCheckingForUpdate.value = true
            _lastCheckResult.value = appUpdateRepository.checkNow(bypassDismiss = true)
            _isCheckingForUpdate.value = false
        }
    }
}
