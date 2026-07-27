package com.android.streamhub.nav

import androidx.lifecycle.ViewModel
import com.android.streamhub.core.common.nav.Route
import com.android.streamhub.core.common.ui.AppLaunchState
import com.android.streamhub.settings.AppLaunchDestination
import com.android.streamhub.settings.AppUiSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves the "On App Launch" setting into a one-time nav redirect - see AppLaunchState for why
 * this only ever fires once per process. Route.LIVE_TV_PATTERN is the NavHost's own
 * startDestination already, so that case needs no redirect at all.
 */
@HiltViewModel
class AppLaunchRedirectViewModel @Inject constructor(
    private val appUiSettingsRepository: AppUiSettingsRepository,
    private val appLaunchState: AppLaunchState,
) : ViewModel() {

    suspend fun resolveRedirectRouteIfNeeded(): String? {
        if (appLaunchState.hasAppliedLaunchDestination) return null
        appLaunchState.hasAppliedLaunchDestination = true
        return when (appUiSettingsRepository.settingsFlow.first().launchDestination) {
            AppLaunchDestination.LIVE_TV -> null
            AppLaunchDestination.VOD -> Route.VOD_PATTERN
            AppLaunchDestination.JELLYFIN -> Route.JELLYFIN_HOME_PATTERN
            AppLaunchDestination.EMBY -> Route.EMBY_HOME_PATTERN
        }
    }
}
