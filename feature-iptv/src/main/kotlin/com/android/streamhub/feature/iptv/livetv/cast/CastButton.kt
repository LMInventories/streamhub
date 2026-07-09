package com.android.streamhub.feature.iptv.livetv.cast

import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * MediaRouteButton is a plain Android View (the Cast SDK has no Compose-native button), wired up
 * via CastButtonFactory the same way a menu-based Cast button would be - AndroidView is the
 * standard bridge for a View-based component with no Compose equivalent. MediaRouteButton tracks
 * its own connected/available visual state internally (driven by the Cast SDK's router state), so
 * there's nothing to pass in for that. Falls back to a plain (non-functional) cast icon when
 * isAvailable is false, e.g. no Google Play Services on this device, rather than hiding the
 * button and shifting the rest of the row around. White tint on the fallback matches the settings
 * icon it sits beside - both overlay video content, not a themed surface.
 *
 * MediaRouteButton reads AppCompat-only theme attributes (mediaRouteButtonStyle and the styled
 * drawables it points to) to build its own background/icon - this app's manifest theme is the
 * platform Theme.Material, not AppCompat, so constructing the button directly against the
 * Activity's own theme crashed with a Resources.NotFoundException at inflation. Wrapping just this
 * button's own Context in a ContextThemeWrapper carrying an AppCompat theme fixes that without
 * touching the app's real theme anywhere else - this is the standard workaround for a
 * non-AppCompat app pulling in one AppCompat-based widget.
 */
@Composable
fun CastButton(isAvailable: Boolean, modifier: Modifier = Modifier) {
    if (!isAvailable) {
        IconButton(onClick = {}, enabled = false, modifier = modifier) {
            Icon(Icons.Outlined.Cast, contentDescription = "Cast unavailable", tint = Color.White)
        }
        return
    }
    AndroidView(
        modifier = modifier.size(48.dp),
        factory = { context ->
            val themedContext = ContextThemeWrapper(context, AppCompatR.style.Theme_AppCompat_NoActionBar)
            // Belt-and-braces beyond the theme fix above - if constructing/wiring the button still
            // throws for some device-specific reason (outdated Play Services, misconfigured route
            // selector, etc.), fall back to a plain android.view.View rather than retrying
            // MediaRouteButton - a second attempt at the same construction that just failed would
            // just throw again, unprotected. A dead placeholder view beats taking the whole Live
            // TV screen down with it.
            runCatching {
                MediaRouteButton(themedContext).apply {
                    CastButtonFactory.setUpMediaRouteButton(themedContext, this)
                }
            }.getOrElse { e ->
                Log.w("CastButton", "Failed to set up MediaRouteButton", e)
                View(context)
            }
        },
    )
}
