package com.android.streamhub.feature.jellyfin.data

import javax.inject.Inject
import javax.inject.Singleton

sealed class JellyfinSubtitleChoice {
    data object Off : JellyfinSubtitleChoice()
    // [index] (not just [language]) is what disambiguates two tracks sharing a language - e.g. a
    // "Full" and a "Forced/Signs" English track on the same release, a real and increasingly common
    // case now that forced tracks are handled specially (see resolveDefaultSubtitleChoice below).
    data class Track(val index: Int, val language: String?) : JellyfinSubtitleChoice()
}

data class JellyfinAudioChoice(val index: Int, val language: String?)

/**
 * The subtitle choice to commit as this item's default before any user tap - shared by the detail
 * screen's own hydration (JellyfinItemDetailViewModel.hydrateDefaultSubtitle) and
 * resolveSubtitlePreference's Downloads-bypass fallback (that path never visits the detail screen,
 * so it needs to land on the exact same default), so the two can never resolve differently for the
 * same item.
 *
 * Precedence: an app-wide language match wins UNLESS the item's own audio is already in that
 * language - in that case a forced track (translating just the bits the viewer wouldn't
 * understand, e.g. the one foreign-language scene in an otherwise-English film) is preferred over
 * the full track, since showing the full track would just be redundant with audio the viewer
 * already understands. No language preference at all still defaults to a forced track over Off, if
 * one exists - a forced track isn't really a "language preference" choice, it's translating
 * something the viewer can't otherwise follow regardless of what language they prefer.
 */
fun JellyfinItemInfo.resolveDefaultSubtitleChoice(preferredLanguage: String?): JellyfinSubtitleChoice {
    val forcedTracks = subtitleTracks.filter { it.isForced }
    val audioLanguage = audioTracks.firstOrNull { it.isDefault }?.language ?: audioTracks.firstOrNull()?.language

    fun forcedChoice(): JellyfinSubtitleChoice.Track? =
        (forcedTracks.firstOrNull { it.language == preferredLanguage } ?: forcedTracks.firstOrNull())
            ?.let { JellyfinSubtitleChoice.Track(it.index, it.language) }

    val smartForced = if (preferredLanguage != null && audioLanguage == preferredLanguage) forcedChoice() else null
    val languageMatch = preferredLanguage
        ?.let { lang -> subtitleTracks.firstOrNull { it.language == lang } }
        ?.let { JellyfinSubtitleChoice.Track(it.index, it.language) }
    val fallbackForced = if (preferredLanguage == null) forcedChoice() else null

    return smartForced ?: languageMatch ?: fallbackForced ?: JellyfinSubtitleChoice.Off
}

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
