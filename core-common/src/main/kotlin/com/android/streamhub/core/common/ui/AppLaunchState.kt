package com.android.streamhub.core.common.ui

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guards the one-time "On App Launch" redirect (see AppLaunchRedirectViewModel) against firing
 * more than once per process. A process-lifetime @Singleton, same shape as FullscreenOverlayState -
 * MainActivity.onCreate() only runs once per process (a real cold-start signal), but nothing else
 * records "have we already acted on this", and merely resuming from background (Activity/
 * composition survives, no new onCreate()) must never re-trigger the redirect.
 */
@Singleton
class AppLaunchState @Inject constructor() {
    var hasAppliedLaunchDestination: Boolean = false
}
