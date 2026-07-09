package com.android.streamhub

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.phone.theme.StreamHubPhoneTheme
import com.android.streamhub.core.ui.tv.theme.StreamHubTvTheme
import com.android.streamhub.nav.PhoneApp
import com.android.streamhub.nav.TvApp
import com.android.streamhub.settings.AppUiSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

// FragmentActivity, not plain ComponentActivity (which it extends anyway, so setContent()/
// enableEdgeToEdge()/Hilt all still work unchanged) - the Live TV Cast button's
// MediaRouteButton.showDialog() needs a FragmentActivity ancestor to host its device-picker
// DialogFragment, and crashes without one.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        setContent {
            val appUiSettingsViewModel: AppUiSettingsViewModel = hiltViewModel()
            val appUiState by appUiSettingsViewModel.uiState.collectAsStateWithLifecycle()

            // Palette is a mutableStateOf-backed singleton (see its own doc comment), not a
            // CompositionLocal, so this single call site is what propagates the user's theme
            // choice to every screen in the app - no other wiring needed anywhere else.
            LaunchedEffect(appUiState.themeMode) { Palette.applyMode(appUiState.themeMode) }

            val baseDensity = LocalDensity.current
            val scaledDensity = Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * appUiState.textScale.multiplier,
            )

            // Overriding LocalDensity here (rather than scaling AppTextStyles' individual sp
            // values) scales every sp-based text size in the app, including nested screens that
            // wrap their own local MaterialTheme further down - LocalDensity isn't something
            // MaterialTheme overrides, so it survives through all of them unchanged.
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                if (isTv) {
                    StreamHubTvTheme { TvApp() }
                } else {
                    StreamHubPhoneTheme { PhoneApp() }
                }
            }
        }
    }
}
