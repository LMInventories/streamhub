package com.android.streamhub

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.android.streamhub.core.ui.phone.theme.StreamHubPhoneTheme
import com.android.streamhub.core.ui.tv.theme.StreamHubTvTheme
import com.android.streamhub.nav.PhoneApp
import com.android.streamhub.nav.TvApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
