package com.android.streamhub.nav

import androidx.lifecycle.ViewModel
import com.android.streamhub.core.common.ui.FullscreenOverlayState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin bridge so PhoneApp/TvApp (plain composables, not themselves ViewModels) can observe the
 * shared FullscreenOverlayState singleton via hiltViewModel() - Hilt's Composable injection is
 * ViewModel-shaped, this just exposes the one field those two need.
 */
@HiltViewModel
class FullscreenOverlayViewModel @Inject constructor(
    fullscreenOverlayState: FullscreenOverlayState,
) : ViewModel() {
    val isActive: StateFlow<Boolean> = fullscreenOverlayState.isActive
}
