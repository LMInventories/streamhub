package com.android.streamhub.core.common.domain

data class WatchProgress(val positionMs: Long, val durationMs: Long) {
    val fractionComplete: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    /** Close enough to the end that resuming would just replay the credits - treat as finished, offer "Play" again rather than "Resume". */
    val isNearlyComplete: Boolean
        get() = fractionComplete > 0.92f
}

/**
 * Cross-source playback position tracking (VOD movies/episodes today, usable by Jellyfin/Emby
 * later) - lives here as an interface rather than a concrete store because the writer
 * (feature-player-screen, while/after playing) and the reader (feature-iptv, deciding
 * Play-vs-Resume before playing) are two feature modules that don't depend on each other. The
 * concrete Room-backed implementation is in :core-player, which both already depend on.
 */
interface WatchProgressRepository {
    suspend fun getProgress(sourceType: SourceType, itemId: String): WatchProgress?
    suspend fun saveProgress(sourceType: SourceType, itemId: String, positionMs: Long, durationMs: Long)
}
