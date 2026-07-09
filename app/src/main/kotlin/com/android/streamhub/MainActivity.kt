package com.android.streamhub

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.android.streamhub.core.ui.phone.theme.StreamHubPhoneTheme
import com.android.streamhub.core.ui.tv.theme.StreamHubTvTheme
import com.android.streamhub.nav.PhoneApp
import com.android.streamhub.nav.TvApp
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
            if (isTv) {
                StreamHubTvTheme { TvApp() }
            } else {
                StreamHubPhoneTheme { PhoneApp() }
            }
        }
    }
}
