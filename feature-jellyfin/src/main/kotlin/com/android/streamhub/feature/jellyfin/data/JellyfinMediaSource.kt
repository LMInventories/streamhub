package com.android.streamhub.feature.jellyfin.data

import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.NextPlaybackItem
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.PlaybackSegments
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.domain.SubtitlePreference
import com.android.streamhub.core.common.domain.TrickplayInfo
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
    private val playbackPreferenceStore: JellyfinPlaybackPreferenceStore,
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
        }.map {
            it.toPlaybackItem(
                streamUri = "",
                preferredAudio = null,
                preferredSubtitle = null,
                subtitlesOff = false,
                forcedSubtitle = null,
                trickplay = null,
            )
        }
    }

    override suspend fun resolvePlayback(itemId: String): PlaybackItem {
        val item = browseRepository.getItem(itemId) ?: error("Jellyfin item not found: $itemId")
        val preference = playbackPreferenceStore.get(itemId)
        val streamUrl = browseRepository.getStreamUrl(itemId, preference?.mediaSourceId)
            ?: error("No Jellyfin stream URL for: $itemId")
        val settings = appSettingsRepository.settingsFlow.first()
        val subtitlePreference = resolveSubtitlePreference(item, settings, preference)
        // Same "explicit per-item choice overrides the app-wide setting" precedence subtitles
        // already use below - no store entry (the Downloads-list bypass path, which never visits
        // the detail screen) falls straight through to the app-wide setting exactly as before this
        // picker existed.
        val preferredAudio = preference?.audio?.language ?: settings.preferredAudioLanguage
        return item.toPlaybackItem(
            streamUri = streamUrl,
            preferredAudio = preferredAudio,
            preferredSubtitle = subtitlePreference.preferredLanguage,
            subtitlesOff = subtitlePreference.off,
            forcedSubtitle = subtitlePreference.forcedLanguage,
            trickplay = item.trickplayInfo?.let { info ->
                browseRepository.trickplayTileUrlTemplate(itemId, info)?.let { template ->
                    TrickplayInfo(
                        tileUrlTemplate = template,
                        width = info.width,
                        height = info.height,
                        tileGridColumns = info.tileGridColumns,
                        tileGridRows = info.tileGridRows,
                        thumbnailCount = info.thumbnailCount,
                        intervalMs = info.intervalMs,
                    )
                }
            },
        )
    }

    // An explicit per-item choice from the detail page's subtitle dropdown (if any) overrides the
    // app-wide language preference below - that's the whole point of offering a per-item picker at
    // all. No choice recorded for this item falls through to resolveDefaultSubtitleChoice's own
    // precedence (app-wide language match, else a forced track, else hard Off) - the detail page's
    // own dropdown resolves and commits the exact same default via that shared function, so the two
    // can never disagree by the time Play is pressed. Also consulted for already-downloaded playback
    // (see PlayerViewModel.loadAndPrepare) - that path never calls resolvePlayback() above at all
    // (deliberately, to stay usable offline), so without this the default here would always fall
    // back to PlaybackItem's own default (subtitlesOff = false) instead of a real decision.
    override suspend fun resolveSubtitlePreference(itemId: String): SubtitlePreference {
        val item = browseRepository.getItem(itemId) ?: return SubtitlePreference()
        val settings = appSettingsRepository.settingsFlow.first()
        return resolveSubtitlePreference(item, settings, playbackPreferenceStore.get(itemId))
    }

    // A forced track is deliberately allowed to survive an explicit Off choice below (unlike every
    // other subtitle behavior, which Off hard-disables) - "Off" means "no regular subtitles", not
    // "ignore dialogue the viewer can't otherwise follow". Off with no forced track available still
    // falls through to true hard-off exactly as before. A Track choice only carries forcedLanguage
    // when the chosen track itself happens to be a forced one (e.g. the user picked it directly from
    // the picker, or resolveDefaultSubtitleChoice already landed on it) - that still routes through
    // PlayerController's precise forced-track search instead of plain language matching, since a
    // forced and a full track can share the same language (common on e.g. anime releases).
    private fun resolveSubtitlePreference(
        item: JellyfinItemInfo,
        settings: JellyfinAppSettings,
        preference: JellyfinPlaybackPreference?,
    ): SubtitlePreference {
        val subtitleChoice = preference?.subtitle ?: item.resolveDefaultSubtitleChoice(settings.preferredSubtitleLanguage)
        return when (subtitleChoice) {
            is JellyfinSubtitleChoice.Off -> {
                val forcedTracks = item.subtitleTracks.filter { it.isForced }
                val forced = forcedTracks.firstOrNull { it.language == settings.preferredSubtitleLanguage } ?: forcedTracks.firstOrNull()
                SubtitlePreference(preferredLanguage = forced?.language, off = forced == null, forcedLanguage = forced?.language)
            }
            is JellyfinSubtitleChoice.Track -> {
                val isForced = item.subtitleTracks.firstOrNull { it.index == subtitleChoice.index }?.isForced == true
                SubtitlePreference(
                    preferredLanguage = subtitleChoice.language,
                    off = false,
                    forcedLanguage = subtitleChoice.language.takeIf { isForced },
                )
            }
        }
    }

    override suspend fun resolvePlaybackSegments(itemId: String): PlaybackSegments? = browseRepository.getMediaSegments(itemId)

    /**
     * Deliberately computed from the season's own episode list rather than Jellyfin's Next Up API:
     * Next Up is driven by *watched* state, so asking it mid-episode returns the episode currently
     * playing, not the one after it. Walks to the next episode in this season by index, then falls
     * through to the first episode of the next season when the current one runs out - so finishing
     * a season finale rolls into the new season the way it should, instead of just stopping.
     */
    override suspend fun resolveNextItem(itemId: String): NextPlaybackItem? {
        val current = browseRepository.getItem(itemId) ?: return null
        if (current.type != JellyfinItemType.EPISODE) return null
        val seriesId = current.seriesId ?: return null
        val currentIndex = current.indexNumber

        val sameSeasonNext = current.seasonId
            ?.let { seasonId -> browseRepository.getEpisodes(seriesId, seasonId) }
            ?.filter { it.indexNumber != null && currentIndex != null && it.indexNumber > currentIndex }
            ?.minByOrNull { it.indexNumber!! }

        val next = sameSeasonNext ?: nextSeasonFirstEpisode(seriesId, current.parentIndexNumber)
        return next?.toNextPlaybackItem()
    }

    private suspend fun nextSeasonFirstEpisode(seriesId: String, currentSeasonNumber: Int?): JellyfinItemInfo? {
        if (currentSeasonNumber == null) return null
        val nextSeason = browseRepository.getSeasons(seriesId)
            .filter { it.indexNumber != null && it.indexNumber > currentSeasonNumber }
            .minByOrNull { it.indexNumber!! }
            ?: return null
        return browseRepository.getEpisodes(seriesId, nextSeason.id).minByOrNull { it.indexNumber ?: Int.MAX_VALUE }
    }

    private fun JellyfinItemInfo.toNextPlaybackItem(): NextPlaybackItem = NextPlaybackItem(
        id = id,
        title = name,
        seriesName = seriesName,
        episodeLabel = if (parentIndexNumber != null && indexNumber != null) {
            "Season $parentIndexNumber · Episode $indexNumber"
        } else {
            null
        },
        description = overview,
        // Same "prefer the real scene grab over the series-poster override" rule the detail screen's
        // own episode thumbnails use - see JellyfinBrowseRepository.toItemInfo.
        thumbnailUrl = episodeThumbnailUrl ?: primaryImageUrl,
    )

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
        forcedSubtitle: String?,
        trickplay: TrickplayInfo?,
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
        forcedSubtitleLanguage = forcedSubtitle,
        trickplay = trickplay,
    )
}
