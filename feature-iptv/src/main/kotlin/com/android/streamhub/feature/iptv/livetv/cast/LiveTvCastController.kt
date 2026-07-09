package com.android.streamhub.feature.iptv.livetv.cast

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
 * Send-and-forget casting, not a full CastPlayer swap-in for the existing local ExoPlayer
 * playback path - the connected Cast receiver plays the stream URL independently. Simpler and
 * lower-risk than replacing PlayerController's Player instance with CastPlayer whenever a session
 * connects, and still delivers "cast the channel I'm watching to the TV", which is what was asked
 * for.
 */
@Singleton
class LiveTvCastController @Inject constructor(@ApplicationContext context: Context) {

    // Null when Google Play Services / the Cast SDK isn't available on this device - some IPTV
    // Android TV boxes ship without GMS - every public method below no-ops in that case rather
    // than crashing.
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
    fun loadStream(streamUrl: String, title: String, subtitle: String?) {
        val remoteMediaClient = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
        }
        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(contentTypeFor(streamUrl))
            .setMetadata(metadata)
            .build()
        remoteMediaClient.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    // Xtream/M3U live URLs almost never carry a useful extension (Xtream's default output is raw
    // MPEG-TS regardless of URL shape), so this is a best-effort guess for the receiver's
    // benefit, not a reliable content-type sniff - HLS is the one case worth detecting explicitly
    // since its mime type is otherwise nothing like the container it wraps.
    private fun contentTypeFor(streamUrl: String): String =
        if (streamUrl.substringBefore('?').endsWith(".m3u8")) "application/x-mpegURL" else "video/mp2t"
}
