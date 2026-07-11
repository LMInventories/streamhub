package com.android.streamhub.feature.jellyfin.data

import javax.inject.Inject
import javax.inject.Singleton

sealed class JellyfinSubtitleChoice {
    data object Off : JellyfinSubtitleChoice()
    data class Track(val language: String?) : JellyfinSubtitleChoice()
}

/**
 * Bridges the subtitle choice made on the item detail page's dropdown to resolvePlayback(), which
 * runs in a different ViewModel/screen entirely once Play is tapped and has no other way to know
 * about it. In-memory and keyed by itemId only (not persisted) - this is meant to survive exactly
 * the hop from "user picked something" to "that item's next playback resolves", not to be a
 * durable per-user setting the way the app-wide preferred-language setting is.
 */
@Singleton
class JellyfinSubtitlePreferenceStore @Inject constructor() {
    private val choices = mutableMapOf<String, JellyfinSubtitleChoice>()

    fun set(itemId: String, choice: JellyfinSubtitleChoice?) {
        if (choice == null) choices.remove(itemId) else choices[itemId] = choice
    }

    fun get(itemId: String): JellyfinSubtitleChoice? = choices[itemId]
}
