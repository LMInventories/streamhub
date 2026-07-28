package com.android.streamhub.core.common.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * One backend (IPTV provider, Jellyfin server, Emby server, ...) that can list and resolve
 * playable items. Every feature module implements this and registers itself into the
 * MediaSourceRegistry multibinding so Home/Search can iterate all sources without knowing
 * their concrete types.
 */
interface MediaSource {
    val sourceType: SourceType

    suspend fun browse(): List<PlaybackItem>

    /**
     * Re-resolve an item right before playback starts. Trivial for static sources, but this is
     * the seam real backends need: Xtream stream-URL construction, Jellyfin/Emby PlaybackInfo
     * negotiation and auth-token refresh. Called from the same site regardless of source so
     * those backends slot in without changing the player/nav call sites.
     */
    suspend fun resolvePlayback(itemId: String): PlaybackItem

    /**
     * Called by the player screen when a live item starts playing. Default no-op - only sources
     * with a meaningful "recently viewed" concept (live TV channels) need to track this, so the
     * player screen can call it unconditionally without knowing which sources care.
     */
    suspend fun recordViewed(itemId: String) {}

    /** Default empty - see [recordViewed]. Drives the fullscreen player's channel-switcher strip for sources that support it. */
    fun observeRecentlyViewed(): Flow<List<PlaybackItem>> = flowOf(emptyList())

    /**
     * Playback lifecycle hooks for sources that track watched/resume state server-side
     * (Jellyfin/Emby), so the server's own apps see the same progress and watched status this app
     * produces instead of it staying siloed in this app's local WatchProgressRepository. Default
     * no-op - only sources with a server-side concept of this need to override any of them, so the
     * player screen can call all three unconditionally regardless of source, the same pattern as
     * [recordViewed] above. Not used for live items (those already have [recordViewed]).
     */
    suspend fun onPlaybackStarted(itemId: String) {}

    suspend fun onPlaybackProgress(itemId: String, positionMs: Long, durationMs: Long, isPaused: Boolean) {}

    suspend fun onPlaybackStopped(itemId: String, positionMs: Long, durationMs: Long) {}

    /**
     * Subtitle preference for an item that's already available offline - unlike [resolvePlayback],
     * this must never require network access, since the player also calls it for already-downloaded
     * content (which plays back straight from disk specifically so a genuinely offline device can
     * still use it). Default "no preference" - only sources with a per-item subtitle picker
     * (Jellyfin) need to override this.
     */
    suspend fun resolveSubtitlePreference(itemId: String): SubtitlePreference = SubtitlePreference()
}

/** [preferredLanguage] mirrors PlaybackItem's own field; [off] is the hard "Off means Off" override - see PlaybackItem.subtitlesOff's doc for why that's distinct from just no language preference. */
data class SubtitlePreference(
    val preferredLanguage: String? = null,
    val off: Boolean = false,
)
