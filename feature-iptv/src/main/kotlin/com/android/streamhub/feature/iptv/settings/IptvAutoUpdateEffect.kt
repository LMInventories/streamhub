package com.android.streamhub.feature.iptv.settings

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.IptvAutoUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IptvAutoUpdateCheckViewModel @Inject constructor(
    private val autoUpdateRepository: IptvAutoUpdateRepository,
) : ViewModel() {
    fun checkAndRefreshIfDue() {
        viewModelScope.launch { autoUpdateRepository.checkAndRefreshIfDue() }
    }
}

/**
 * Call once from the app's root composable (outside any NavHost, so it resolves to an
 * Activity-scoped ViewModel rather than a nav-backstack-entry-scoped one). ON_START covers both
 * a genuine cold start ("On App Open") and resuming from the background, which is also how the
 * "every N hours/days" policies actually get a chance to fire without needing a true background
 * scheduler (WorkManager) - deliberately out of scope here, see IptvAutoUpdateRepository.
 */
@Composable
fun IptvAutoUpdateEffect(viewModel: IptvAutoUpdateCheckViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.checkAndRefreshIfDue()
    }
}
