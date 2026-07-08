package com.android.streamhub.core.common.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets a screen hide the app's own bottom nav bar (phone) / tab row (TV) without navigating to a
 * separate route - the normal PhoneScaffold/TvScaffold visibility is route-based
 * (bottomBarVisible = currentRoute in TAB_ROUTES), which doesn't know about a child screen's own
 * internal UI state. An in-place fullscreen toggle (e.g. Live TV's mini-preview expanding to fill
 * the screen using the same player instance, rather than navigating to a fresh player screen)
 * needs this extra signal to actually reach edge-to-edge, not just fill the space Scaffold
 * already reserves for content.
 */
@Singleton
class FullscreenOverlayState @Inject constructor() {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    fun setActive(active: Boolean) {
        _isActive.value = active
    }
}
