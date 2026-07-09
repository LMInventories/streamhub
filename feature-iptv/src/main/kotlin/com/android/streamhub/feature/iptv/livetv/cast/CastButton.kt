package com.android.streamhub.feature.iptv.livetv.cast

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
            MediaRouteButton(context).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
            }
        },
    )
}
