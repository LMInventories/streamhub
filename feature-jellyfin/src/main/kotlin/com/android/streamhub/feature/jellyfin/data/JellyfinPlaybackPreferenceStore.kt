package com.android.streamhub.feature.jellyfin.data

import javax.inject.Inject
import javax.inject.Singleton

sealed class JellyfinSubtitleChoice {
    data object Off : JellyfinSubtitleChoice()
    data class Track(val language: String?) : JellyfinSubtitleChoice()
}

data class JellyfinAudioChoice(val index: Int, val language: String?)

/** The three per-item playback choices made on the detail screen's pickers - see JellyfinPlaybackPreferenceStore's own doc for why they're bundled together. */
data class JellyfinPlaybackPreference(
    val subtitle: JellyfinSubtitleChoice? = null,
    val audio: JellyfinAudioChoice? = null,
    val mediaSourceId: String? = null,
)

/**
 * Bridges the choices made on the item detail page's pickers (subtitle track, audio track, video
 * version) to resolvePlayback(), which runs in a different ViewModel/screen entirely once Play is
 * tapped and has no other way to know about them. In-memory and keyed by itemId only (not
 * persisted) - this is meant to survive exactly the hop from "detail screen resolved a choice" to
 * "that item's next playback resolves", not to be a durable per-user setting the way the app-wide
 * preferred-language setting is. The detail screen writes a definitive default here the moment the
 * item loads (see JellyfinItemDetailViewModel's hydrateDefault* functions) - unlike an early
 * version of this store, callers should never see themselves needing to reason about a missing
 * entry meaning "untouched" versus "no preference".
 */
@Singleton
class JellyfinPlaybackPreferenceStore @Inject constructor() {
    private val choices = mutableMapOf<String, JellyfinPlaybackPreference>()

    fun get(itemId: String): JellyfinPlaybackPreference? = choices[itemId]

    fun setSubtitle(itemId: String, choice: JellyfinSubtitleChoice) {
        choices[itemId] = (choices[itemId] ?: JellyfinPlaybackPreference()).copy(subtitle = choice)
    }

    fun setAudio(itemId: String, choice: JellyfinAudioChoice) {
        choices[itemId] = (choices[itemId] ?: JellyfinPlaybackPreference()).copy(audio = choice)
    }

    fun setMediaSourceId(itemId: String, mediaSourceId: String) {
        choices[itemId] = (choices[itemId] ?: JellyfinPlaybackPreference()).copy(mediaSourceId = mediaSourceId)
    }
}
