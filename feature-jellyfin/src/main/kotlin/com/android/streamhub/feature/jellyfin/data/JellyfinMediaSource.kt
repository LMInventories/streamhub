package com.android.streamhub.feature.jellyfin.data

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.domain.SubtitlePreference
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Ticks are Jellyfin's .NET-derived 100ns unit throughout its API - 10_000 ticks/ms.
private const val TICKS_PER_MS = 10_000L

/**
 * Cross-source adapter (Master Search/Favorites, once those exist) over JellyfinBrowseRepository.
 * The dedicated home/library/detail screens in this module talk to JellyfinBrowseRepository
 * directly instead of through here - PlaybackItem is a deliberately flattened, source-agnostic
 * shape that would lose the season/episode/cast structure those screens need.
 */
@Singleton
class JellyfinMediaSource @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    private val appSettingsRepository: JellyfinAppSettingsRepository,
    private val subtitlePreferenceStore: JellyfinSubtitlePreferenceStore,
) : MediaSource {

    override val sourceType: SourceType = SourceType.JELLYFIN

    override suspend fun browse(): List<PlaybackItem> {
        // A single reasonably-large page per library rather than looping every page - nothing
        // depends on this being a fully exhaustive catalog yet (Master Search doesn't exist
        // in this app), and resolvePlayback() below looks items up directly by id rather than
        // scanning this list, unlike feature-iptv's Xtream adapter.
        return browseRepository.getLibraries().flatMap { library ->
            val itemType = when (library.type) {
                JellyfinLibraryType.MOVIES -> JellyfinItemType.MOVIE
                JellyfinLibraryType.TV_SHOWS -> JellyfinItemType.SERIES
            }
            browseRepository.getItems(library.id, itemType, startIndex = 0, limit = 500)
        }.map { it.toPlaybackItem(streamUri = "", preferredAudio = null, preferredSubtitle = null, subtitlesOff = false) }
    }

    override suspend fun resolvePlayback(itemId: String): PlaybackItem {
        val item = browseRepository.getItem(itemId) ?: error("Jellyfin item not found: $itemId")
        val streamUrl = browseRepository.getStreamUrl(itemId) ?: error("No Jellyfin stream URL for: $itemId")
        val settings = appSettingsRepository.settingsFlow.first()
        val subtitlePreference = resolveSubtitlePreference(itemId)
        return item.toPlaybackItem(
            streamUri = streamUrl,
            preferredAudio = settings.preferredAudioLanguage,
            preferredSubtitle = subtitlePreference.preferredLanguage,
            subtitlesOff = subtitlePreference.off,
        )
    }

    // An explicit per-item choice from the detail page's subtitle dropdown (if any) overrides the
    // app-wide language preference below - that's the whole point of offering a per-item picker at
    // all. No choice recorded for this item falls through to the app-wide language setting IF one
    // is actually configured - but if neither exists, this must still default to hard-off (not just
    // "no language preference"), because the detail page's own dropdown already displays "Off" as
    // its default, untouched state. Leaving subtitlesOff false here let Media3's default track
    // selector auto-pick a forced/default-flagged embedded track regardless of any of this, which is
    // exactly what broke the "Off means Off" contract. Also consulted for already-downloaded
    // playback (see PlayerViewModel.loadAndPrepare) - that path never calls resolvePlayback() above
    // at all (deliberately, to stay usable offline), so without this override subtitles for a
    // downloaded item would always fall back to PlaybackItem's own default (subtitlesOff = false).
    override suspend fun resolveSubtitlePreference(itemId: String): SubtitlePreference {
        val settings = appSettingsRepository.settingsFlow.first()
        val subtitleChoice = subtitlePreferenceStore.get(itemId)
        val subtitlesOff = when (subtitleChoice) {
            is JellyfinSubtitleChoice.Off -> true
            is JellyfinSubtitleChoice.Track -> false
            null -> settings.preferredSubtitleLanguage == null
        }
        return SubtitlePreference(
            preferredLanguage = when (subtitleChoice) {
                is JellyfinSubtitleChoice.Track -> subtitleChoice.language
                else -> settings.preferredSubtitleLanguage
            },
            off = subtitlesOff,
        )
    }

    override suspend fun onPlaybackStarted(itemId: String) = browseRepository.reportPlaybackStart(itemId)

    override suspend fun onPlaybackProgress(itemId: String, positionMs: Long, durationMs: Long, isPaused: Boolean) =
        browseRepository.reportPlaybackProgress(itemId, positionMs, isPaused)

    override suspend fun onPlaybackStopped(itemId: String, positionMs: Long, durationMs: Long) =
        browseRepository.reportPlaybackStopped(itemId, positionMs, durationMs)

    private fun JellyfinItemInfo.toPlaybackItem(
        streamUri: String,
        preferredAudio: String?,
        preferredSubtitle: String?,
        subtitlesOff: Boolean,
    ): PlaybackItem = PlaybackItem(
        id = id,
        sourceType = SourceType.JELLYFIN,
        title = name,
        subtitle = seriesName,
        posterUrl = primaryImageUrl,
        streamUri = streamUri,
        startPositionMs = resumePositionTicks / TICKS_PER_MS,
        isLive = false,
        preferredAudioLanguage = preferredAudio,
        preferredSubtitleLanguage = preferredSubtitle,
        subtitlesOff = subtitlesOff,
    )
}
