package com.android.streamhub.core.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.streamhub.core.common.domain.PlaybackItem
import javax.inject.Inject

/**
 * Hands playback off to a third-party player app via the standard ACTION_VIEW convention
 * (the same one VLC/MX Player etc. already respond to) instead of building a custom picker.
 */
class ExternalPlayerLauncher @Inject constructor() {

    /** Returns true if an external player (or the system chooser) was launched. */
    fun launch(context: Context, item: PlaybackItem): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(item.streamUri), item.mimeTypeHint ?: "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", item.title)
        }

        return try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
