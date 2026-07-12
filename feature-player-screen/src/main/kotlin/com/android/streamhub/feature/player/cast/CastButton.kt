package com.android.streamhub.feature.player.cast

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
 * Same component as feature-iptv's own CastButton (see that file's doc for the AndroidView/theme
 * workaround details) - duplicated rather than shared across feature modules, matching this app's
 * established convention for small feature-scoped UI (e.g. DownloadButton).
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
