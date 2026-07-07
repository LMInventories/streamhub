package com.android.streamhub.core.common.domain

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
}
