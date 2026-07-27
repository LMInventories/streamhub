package com.android.streamhub.update

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateCheckViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {
    fun checkIfDue() {
        viewModelScope.launch { appUpdateRepository.checkIfDue() }
    }
}

/**
 * Call once from the app's root composable (outside any NavHost, so it resolves to an
 * Activity-scoped ViewModel rather than a nav-backstack-entry-scoped one) - same shape as
 * IptvAutoUpdateEffect. ON_START covers both a genuine cold start and resuming from the
 * background, which is what lets the ~12h throttle in AppUpdateRepository actually get a chance
 * to fire without a true background scheduler (WorkManager, deliberately out of scope here).
 */
@Composable
fun AppUpdateCheckEffect(viewModel: AppUpdateCheckViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.checkIfDue()
    }
}
