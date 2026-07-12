package com.android.streamhub.feature.player.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Send-and-forget casting for the generic player screen (VOD/Jellyfin/downloads) - same shape as
 * feature-iptv's LiveTvCastController (not shared directly - core-player has no Cast SDK
 * dependency, and this app's own convention is to duplicate small feature-scoped UI/controller
 * pairs like this rather than add a cross-feature module dependency for one class). The connected
 * Cast receiver plays the stream URL independently rather than this swapping PlayerController's
 * own ExoPlayer for a CastPlayer.
 */
@Singleton
class PlayerCastController @Inject constructor(@ApplicationContext context: Context) {

    // Null when Google Play Services / the Cast SDK isn't available on this device - every public
    // method below no-ops in that case rather than crashing.
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    val isAvailable: Boolean = castContext != null

    private val _isCasting = MutableStateFlow(castContext?.sessionManager?.currentCastSession?.isConnected == true)
    val isCasting: StateFlow<Boolean> = _isCasting

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) { _isCasting.value = true }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { _isCasting.value = true }
        override fun onSessionEnded(session: CastSession, error: Int) { _isCasting.value = false }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        castContext?.sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
    }

    /** Loads (or replaces) the currently-cast stream. No-op if nothing is connected. */
    fun loadStream(streamUrl: String, title: String, subtitle: String?, isLive: Boolean) {
        val remoteMediaClient = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
        }
        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentTypeFor(streamUrl))
            .setMetadata(metadata)
            .build()
        remoteMediaClient.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    // Best-effort guess for the receiver's benefit, not a reliable content-type sniff - HLS is the
    // one case worth detecting explicitly since its mime type is otherwise nothing like the
    // container it wraps. video/mp4 (not live TV's mpeg-ts default) since VOD/Jellyfin direct-play
    // URLs are overwhelmingly mp4/mkv files, not raw transport streams.
    private fun contentTypeFor(streamUrl: String): String =
        if (streamUrl.substringBefore('?').endsWith(".m3u8")) "application/x-mpegURL" else "video/mp4"
}
