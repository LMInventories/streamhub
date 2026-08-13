package com.android.streamhub.feature.emby.data

import com.android.streamhub.core.common.domain.PlaybackSegments
import com.android.streamhub.core.common.search.FuzzyMatch
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// 1 tick = 100ns (Emby's .NET-derived convention, see EmbyModels.TICKS_PER_MS) - 600_000_000
// ticks/min. Matches JellyfinBrowseRepository's own TICKS_PER_MINUTE constant.
private const val TICKS_PER_MINUTE = 600_000_000L

// Matches WatchProgress.isNearlyComplete (core-common) / JellyfinBrowseRepository's own constant -
// close enough to the end that the server should treat this the same as an explicit "mark
// watched" rather than just a resume point.
private const val NEARLY_COMPLETE_FRACTION = 0.92f

/**
 * The one class making all authenticated Emby calls plus DTO-to-EmbyItemInfo mapping. Reads
 * EmbySourceConfigRepository.configFlow.first() fresh on every call rather than caching a
 * long-lived client the way JellyfinBrowseRepository caches an ApiClient - here the "client" IS
 * EmbyRemoteDataSource, which already caches one Retrofit instance per base URL, so there's
 * nothing extra worth caching at this layer.
 */
@Singleton
class EmbyBrowseRepository @Inject constructor(
    private val remoteDataSource: EmbyRemoteDataSource,
    private val configRepository: EmbySourceConfigRepository,
    private val appSettingsRepository: EmbyAppSettingsRepository,
) {
    /**
     * [includeAppHidden] is for the library-visibility settings screen, which needs to list every
     * library (including ones the user already hid via that same screen) to let them un-hide it -
     * every other caller wants the filtered list, which is why that's the default. Mirrors
     * JellyfinBrowseRepository.getLibraries exactly.
     */
    suspend fun getLibraries(includeAppHidden: Boolean = false): List<EmbyLibraryInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        val libraries = runCatching {
            remoteDataSource.getUserViews(config.serverUrl, config.accessToken, config.userId)
                .items.mapNotNull { it.toLibraryInfo() }
        }.getOrDefault(emptyList())
        if (includeAppHidden) return libraries
        val hiddenIds = appSettingsRepository.settingsFlow.first().hiddenLibraryIds
        return libraries.filter { it.id !in hiddenIds }
    }

    suspend fun getResumeItems(limit: Int = 20): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return runCatching {
            remoteDataSource.getResumeItems(
                baseUrl = config.serverUrl,
                token = config.accessToken,
                userId = config.userId,
                includeItemTypes = "Movie,Episode",
                limit = limit,
            ).items.map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
    }

    suspend fun getNextUp(limit: Int = 20): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return runCatching {
            remoteDataSource.getNextUp(config.serverUrl, config.accessToken, config.userId, limit = limit)
                .items.map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
    }

    /** The one episode a series' own detail screen should surface as "Next Up" - null once the whole series is caught up, or if nothing's ever been started. */
    suspend fun getNextUp(seriesId: String): EmbyItemInfo? {
        val config = configRepository.configFlow.first() ?: return null
        return runCatching {
            remoteDataSource.getNextUp(config.serverUrl, config.accessToken, config.userId, seriesId = seriesId, limit = 1)
                .items.firstOrNull()?.toItemInfo(config)
        }.getOrNull()
    }

    suspend fun getLatestMedia(libraryId: String, limit: Int = 20): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return runCatching {
            remoteDataSource.getLatestItems(config.serverUrl, config.accessToken, config.userId, parentId = libraryId, limit = limit)
                .map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
    }

    /**
     * Paginated library browse - libraryId identifies which library (Movies/TV Shows), itemType
     * picks which Emby item-type string to filter to since a "TV Shows" library's items() call
     * needs Series, not Episode.
     */
    suspend fun getItems(
        libraryId: String,
        itemType: EmbyItemType,
        startIndex: Int,
        limit: Int,
        sortOption: EmbySortOption = EmbySortOption.NAME_ASC,
    ): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        val kind = when (itemType) {
            EmbyItemType.MOVIE -> "Movie"
            EmbyItemType.SERIES -> "Series"
            else -> return emptyList()
        }
        val (sortBy, sortOrder) = sortOption.toSortByAndOrder()
        return runCatching {
            remoteDataSource.getItems(
                baseUrl = config.serverUrl,
                token = config.accessToken,
                userId = config.userId,
                parentId = libraryId,
                includeItemTypes = kind,
                recursive = true,
                sortBy = sortBy,
                sortOrder = sortOrder,
                startIndex = startIndex,
                limit = limit,
            ).items.map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
    }

    suspend fun getItem(itemId: String): EmbyItemInfo? {
        val config = configRepository.configFlow.first() ?: return null
        return runCatching {
            remoteDataSource.getItem(config.serverUrl, config.accessToken, config.userId, itemId).toItemInfo(config)
        }.getOrNull()
    }

    /**
     * Broad server-side search (SearchTerm) plus client-side FuzzyMatch narrowing, same pattern
     * as JellyfinBrowseRepository.search() - see that function's doc for why the server call is
     * deliberately over-broad (first word only, higher limit) before FuzzyMatch narrows the
     * result back down to a real match against the full query.
     */
    suspend fun search(query: String, limit: Int = 20): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        if (query.isBlank()) return emptyList()
        val broadTerm = query.trim().substringBefore(' ')
        val results = runCatching {
            remoteDataSource.getItems(
                baseUrl = config.serverUrl,
                token = config.accessToken,
                userId = config.userId,
                searchTerm = broadTerm,
                includeItemTypes = "Movie,Series,Episode",
                recursive = true,
                limit = limit * 4,
            ).items.map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
        return results.filter { FuzzyMatch.matches(it.name, query) }.take(limit)
    }

    /** Favorites can be a mix of movies and series (unlike getItems, which is scoped to one library/kind), and span every library rather than one - so this is its own call rather than getItems with an extra flag. Mirrors JellyfinBrowseRepository.getFavorites exactly. */
    suspend fun getFavorites(startIndex: Int, limit: Int): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return runCatching {
            remoteDataSource.getItems(
                baseUrl = config.serverUrl,
                token = config.accessToken,
                userId = config.userId,
                includeItemTypes = "Movie,Series",
                recursive = true,
                isFavorite = true,
                sortBy = "SortName",
                sortOrder = "Ascending",
                startIndex = startIndex,
                limit = limit,
            ).items.map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
    }

    /**
     * Returns the new favorite state on success, null if the call failed (caller should leave the
     * UI state unchanged). Unlike Jellyfin's SDK, these are raw REST calls whose response bodies
     * carry no confirmation of the new state - success just means the server accepted the
     * mark/unmark request, so the confirmed new state is simply the inverse of what it was before
     * the call. Mirrors JellyfinBrowseRepository.toggleFavorite's contract exactly.
     */
    suspend fun toggleFavorite(itemId: String, currentlyFavorite: Boolean): Boolean? {
        val config = configRepository.configFlow.first() ?: return null
        return runCatching {
            val response = if (currentlyFavorite) {
                remoteDataSource.unmarkFavoriteItem(config.serverUrl, config.accessToken, config.userId, itemId)
            } else {
                remoteDataSource.markFavoriteItem(config.serverUrl, config.accessToken, config.userId, itemId)
            }
            if (!response.isSuccessful) return@runCatching null
            !currentlyFavorite
        }.getOrNull()
    }

    /** Returns the new played state on success, null if the call failed - same "confirmed state is just the inverse on HTTP success" contract as toggleFavorite above. */
    suspend fun toggleWatched(itemId: String, currentlyPlayed: Boolean): Boolean? {
        val config = configRepository.configFlow.first() ?: return null
        return runCatching {
            val response = if (currentlyPlayed) {
                remoteDataSource.unmarkPlayedItem(config.serverUrl, config.accessToken, config.userId, itemId)
            } else {
                remoteDataSource.markPlayedItem(config.serverUrl, config.accessToken, config.userId, itemId)
            }
            if (!response.isSuccessful) return@runCatching null
            !currentlyPlayed
        }.getOrNull()
    }

    suspend fun getSeasons(seriesId: String): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return runCatching {
            remoteDataSource.getSeasons(config.serverUrl, config.accessToken, seriesId, config.userId)
                .items.map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
    }

    suspend fun getEpisodes(seriesId: String, seasonId: String): List<EmbyItemInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return runCatching {
            remoteDataSource.getEpisodes(config.serverUrl, config.accessToken, seriesId, config.userId, seasonId)
                .items.map { it.toItemInfo(config) }
        }.getOrDefault(emptyList())
    }

    /**
     * A direct-play stream URL - ExoPlayer makes its own separate HTTP request outside this
     * module's Retrofit client, so the access token has to travel as an `api_key` query param
     * rather than the custom auth headers every other call in this file sends. Same reasoning as
     * JellyfinBrowseRepository.getStreamUrl. Prefers direct play (a plain `/Videos/{id}/stream`
     * URL) whenever the server says the source supports it, else falls back to the
     * PlaybackInfo-supplied transcodingUrl.
     *
     * maxStreamingBitrateMbps only ever narrows things - passing it as
     * EmbyPlaybackInfoRequest.maxStreamingBitrate lets the server decide whether the source is
     * already under the cap (direct play still happens exactly as before) or needs transcoding to
     * fit it, in which case the response's MediaSourceInfo carries a ready-to-use transcodingUrl
     * instead of us building the direct-play URL ourselves. Mirrors
     * JellyfinBrowseRepository.getStreamUrl's exact Mbps-to-bps conversion.
     */
    suspend fun getStreamUrl(itemId: String, mediaSourceId: String? = null): String? {
        val config = configRepository.configFlow.first() ?: return null
        val maxBitrateBps = appSettingsRepository.settingsFlow.first().maxStreamingBitrateMbps?.let { it * 1_000_000 }
        val mediaSources = runCatching {
            remoteDataSource.getPlaybackInfo(
                config.serverUrl,
                config.accessToken,
                itemId,
                config.userId,
                maxStreamingBitrate = maxBitrateBps,
            ).mediaSources
        }.getOrNull().orEmpty()
        // An explicit per-item version choice from the detail page's Version picker (if any)
        // selects a specific entry out of this same list PlaybackInfo already returns for every
        // version - falls back to the first (server's own default) when no choice was made, same
        // as JellyfinBrowseRepository.getStreamUrl.
        val mediaSource = mediaSources.firstOrNull { it.id == mediaSourceId } ?: mediaSources.firstOrNull() ?: return null

        val base = config.serverUrl.trimEnd('/')
        val transcodingUrl = mediaSource.transcodingUrl
        if (!mediaSource.supportsDirectPlay && transcodingUrl != null) {
            val path = if (transcodingUrl.startsWith('/')) transcodingUrl else "/$transcodingUrl"
            return "$base$path".withApiKey(config.accessToken)
        }

        val resolvedMediaSourceId = mediaSource.id ?: itemId
        return "$base/Videos/$itemId/stream?Static=true&mediaSourceId=$resolvedMediaSourceId".withApiKey(config.accessToken)
    }

    /**
     * Playback lifecycle reporting via Emby's Sessions API, same role as
     * JellyfinBrowseRepository's equivalent trio - this is what makes a native Emby client (or
     * the web UI) see the same Continue Watching/resume-position/watched state this app produces.
     * Best-effort/fire-and-forget - a failed report here should never interrupt playback, so
     * every call swallows its own failure.
     */
    suspend fun reportPlaybackStart(itemId: String) {
        val config = configRepository.configFlow.first() ?: return
        runCatching { remoteDataSource.reportPlaybackStart(config.serverUrl, config.accessToken, itemId) }
    }

    suspend fun reportPlaybackProgress(itemId: String, positionMs: Long, isPaused: Boolean) {
        val config = configRepository.configFlow.first() ?: return
        runCatching {
            remoteDataSource.reportPlaybackProgress(config.serverUrl, config.accessToken, itemId, positionMs * TICKS_PER_MS, isPaused)
        }
    }

    /**
     * [positionMs]/[durationMs] decide whether this also explicitly marks the item played -
     * relying on the server's own completion heuristic here would mean this app's "nearly
     * complete" cutoff could disagree with whatever the server assumes, so it's made explicit
     * here to match exactly (same reasoning as JellyfinBrowseRepository.reportPlaybackStopped).
     */
    suspend fun reportPlaybackStopped(itemId: String, positionMs: Long, durationMs: Long) {
        val config = configRepository.configFlow.first() ?: return
        runCatching { remoteDataSource.reportPlaybackStopped(config.serverUrl, config.accessToken, itemId, positionMs * TICKS_PER_MS) }
        if (durationMs > 0 && positionMs.toFloat() / durationMs.toFloat() > NEARLY_COMPLETE_FRACTION) {
            runCatching { remoteDataSource.markPlayedItem(config.serverUrl, config.accessToken, config.userId, itemId) }
        }
    }

    /**
     * A URL template with a literal "{index}" placeholder for the 0-based tile image index - the
     * player substitutes it per tile it actually needs while the user is scrubbing. Mirrors
     * JellyfinBrowseRepository.trickplayTileUrlTemplate's URL shape as a best guess (same
     * "/Videos/{itemId}/Trickplay/{width}/{index}.jpg" convention plausibly carried over from
     * shared Emby/Jellyfin lineage) - UNVERIFIED, see EmbyTrickplayTileDto's doc. If wrong, this
     * is the one function to fix; the failure mode is just "no trickplay thumbnails for Emby"
     * (the player already handles a null/absent PlaybackItem.trickplay gracefully).
     */
    suspend fun trickplayTileUrlTemplate(itemId: String, trickplayInfo: EmbyTrickplayInfo): String? {
        val config = configRepository.configFlow.first() ?: return null
        val base = config.serverUrl.trimEnd('/')
        return "$base/Videos/$itemId/Trickplay/${trickplayInfo.width}/{index}.jpg?MediaSourceId=${trickplayInfo.mediaSourceId}"
            .withApiKey(config.accessToken)
    }

    /**
     * Intro/outro skip markers - unlike every other best-effort/unverified piece of this module,
     * this is deliberately NOT wired to a guessed endpoint. Emby's skip-intro feature is
     * documented as chapter-marker-based (a proprietary `Chapters3.db` populated by the
     * server-side "Detect Episode Intros" scheduled task), Premiere-gated (Emby Server 4.7+,
     * requires an active Emby Premiere subscription), and no public REST API for reading those
     * markers could be found despite searching. Guessing a chapter-endpoint response shape here
     * would risk silently misinterpreting real chapter data (e.g. treating a normal chapter as an
     * intro marker) rather than just cleanly doing nothing - a wrong guess that returns garbage is
     * worse than an honest "not implemented yet" for a feature this speculative. Returns null
     * unconditionally, which the player already treats as "no Skip Intro button, no segment-aware
     * Next Episode timing" - exactly the same graceful-absence contract older Jellyfin servers get
     * when they haven't run segment analysis. Revisit once a real Emby Premiere server/API docs
     * are available to confirm the actual endpoint.
     */
    suspend fun getMediaSegments(itemId: String): PlaybackSegments? = null

    private fun EmbySortOption.toSortByAndOrder(): Pair<String, String> = when (this) {
        EmbySortOption.NAME_ASC -> "SortName" to "Ascending"
        EmbySortOption.NAME_DESC -> "SortName" to "Descending"
        EmbySortOption.DATE_ADDED_NEWEST -> "DateCreated" to "Descending"
        EmbySortOption.RATING_HIGHEST -> "CommunityRating" to "Descending"
        EmbySortOption.RELEASE_DATE_NEWEST -> "PremiereDate" to "Descending"
    }

    private fun EmbyItemDto.toLibraryInfo(): EmbyLibraryInfo? {
        val libraryType = when (collectionType) {
            "movies" -> EmbyLibraryType.MOVIES
            "tvshows" -> EmbyLibraryType.TV_SHOWS
            else -> return null
        }
        return EmbyLibraryInfo(id = id, name = name.orEmpty(), type = libraryType)
    }

    private fun EmbyItemDto.toItemInfo(config: EmbySourceConfig): EmbyItemInfo {
        val itemType = when (type) {
            "Movie" -> EmbyItemType.MOVIE
            "Series" -> EmbyItemType.SERIES
            "Season" -> EmbyItemType.SEASON
            "Episode" -> EmbyItemType.EPISODE
            else -> EmbyItemType.OTHER
        }
        // An episode's own "Primary" image is a screen-grab in Emby's data model too (same
        // shared lineage as Jellyfin's BaseItemDto), not a poster - correct for a 16:9 scene
        // thumbnail, but every place this app renders primaryImageUrl does so as a 2:3 poster
        // (Continue Watching/Next Up rows, poster grids, detail screens), where showing that
        // screen-grab reads as broken/wrong art. SeriesPrimaryImageTag carries the actual series
        // poster's tag for exactly this - falls back to the episode's own primary only if the
        // series tag is somehow unavailable. Mirrors JellyfinBrowseRepository.toItemInfo exactly.
        val primaryTag = imageTags["Primary"]
        val primaryImageUrl = when {
            itemType == EmbyItemType.EPISODE && seriesId != null && seriesPrimaryImageTag != null ->
                imageUrl(config, seriesId, "Primary", seriesPrimaryImageTag)
            primaryTag != null -> imageUrl(config, id, "Primary", primaryTag)
            else -> null
        }
        val backdropImageUrl = backdropImageTags.firstOrNull()?.let { imageUrl(config, id, "Backdrop", it) }
        // Bypasses the series-poster override above - this is the episode's own scene-grab, for
        // contexts that want the real per-episode thumbnail rather than the series poster
        // primaryImageUrl deliberately substitutes in for poster-shaped grids.
        val episodeThumbnailUrl = primaryTag?.takeIf { itemType == EmbyItemType.EPISODE }
            ?.let { imageUrl(config, id, "Primary", it) }

        // First/primary media source's streams drive the track pickers - mirrors
        // JellyfinBrowseRepository.toItemInfo, which also only ever reads the first media source
        // for this. UNVERIFIED wire shape, see EmbyMediaStreamDto's doc.
        val primaryMediaSource = mediaSources.firstOrNull()
        val subtitleTracks = primaryMediaSource?.mediaStreams.orEmpty()
            .filter { it.type == "Subtitle" }
            .map { stream ->
                EmbySubtitleTrackInfo(
                    index = stream.index ?: 0,
                    label = stream.displayTitle ?: stream.language ?: "Subtitle",
                    language = stream.language,
                    isForced = stream.isForced,
                )
            }
        val audioTracks = primaryMediaSource?.mediaStreams.orEmpty()
            .filter { it.type == "Audio" }
            .map { stream ->
                EmbyAudioTrackInfo(
                    index = stream.index ?: 0,
                    label = stream.displayTitle ?: stream.language ?: "Audio",
                    language = stream.language,
                    isDefault = stream.isDefault,
                )
            }
        // Only worth surfacing as a picker when there's an actual choice - a single-version item
        // (the overwhelming majority) just keeps using the plain read-only Video row instead.
        val videoVersions = mediaSources
            .takeIf { it.size > 1 }
            ?.mapIndexed { index, source ->
                EmbyVersionInfo(
                    id = source.id ?: id,
                    label = source.name?.takeIf { it.isNotBlank() } ?: "Version ${index + 1}",
                )
            }
            .orEmpty()
        // Picks the first media source's largest available resolution - mirrors
        // JellyfinBrowseRepository.toItemInfo's own trickplay extraction. Null (no
        // scrubbing-preview thumbnails) whenever the server hasn't analyzed this item yet, or
        // when the DTO shape guess is simply wrong for this server - same graceful-absence
        // contract as everywhere else in this module. UNVERIFIED, see EmbyTrickplayTileDto's doc.
        val trickplayInfo = trickplay.entries.firstOrNull()?.let { (sourceId, byWidth) ->
            byWidth.values.maxByOrNull { it.width }?.let { info ->
                EmbyTrickplayInfo(
                    mediaSourceId = sourceId,
                    width = info.width,
                    height = info.height,
                    tileGridColumns = info.tileWidth,
                    tileGridRows = info.tileHeight,
                    thumbnailCount = info.thumbnailCount,
                    intervalMs = info.interval,
                )
            }
        }

        return EmbyItemInfo(
            id = id,
            name = name.orEmpty(),
            type = itemType,
            overview = overview,
            productionYear = productionYear,
            communityRating = communityRating,
            genres = genres,
            runtimeMinutes = runTimeTicks?.let { (it / TICKS_PER_MINUTE).toInt() },
            primaryImageUrl = primaryImageUrl,
            backdropImageUrl = backdropImageUrl,
            episodeThumbnailUrl = episodeThumbnailUrl,
            seriesId = seriesId,
            seriesName = seriesName,
            seasonId = seasonId,
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
            isFavorite = userData?.isFavorite ?: false,
            isPlayed = userData?.played ?: false,
            playedPercentage = userData?.playedPercentage,
            resumePositionTicks = userData?.playbackPositionTicks ?: 0L,
            cast = people.filter { it.type == "Actor" }.map { person ->
                EmbyCastMember(
                    id = person.id.orEmpty(),
                    name = person.name.orEmpty(),
                    role = person.role,
                    imageUrl = person.primaryImageTag?.let { tag ->
                        imageUrl(config, person.id.orEmpty(), "Primary", tag)
                    },
                )
            },
            childCount = childCount,
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            videoVersions = videoVersions,
            trickplayInfo = trickplayInfo,
        )
    }

    private fun imageUrl(config: EmbySourceConfig, itemId: String, imageType: String, tag: String): String =
        "${config.serverUrl.trimEnd('/')}/Items/$itemId/Images/$imageType?tag=$tag".withApiKey(config.accessToken)

    private fun String.withApiKey(token: String): String {
        val separator = if (contains('?')) '&' else '?'
        return "$this${separator}api_key=$token"
    }
}
