package com.android.streamhub.feature.emby.data

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
) {
    suspend fun getLibraries(): List<EmbyLibraryInfo> {
        val config = configRepository.configFlow.first() ?: return emptyList()
        return runCatching {
            remoteDataSource.getUserViews(config.serverUrl, config.accessToken, config.userId)
                .items.mapNotNull { it.toLibraryInfo() }
        }.getOrDefault(emptyList())
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
     */
    suspend fun getStreamUrl(itemId: String): String? {
        val config = configRepository.configFlow.first() ?: return null
        val mediaSource = runCatching {
            remoteDataSource.getPlaybackInfo(config.serverUrl, config.accessToken, itemId, config.userId).mediaSources
        }.getOrNull()?.firstOrNull() ?: return null

        val base = config.serverUrl.trimEnd('/')
        val transcodingUrl = mediaSource.transcodingUrl
        if (!mediaSource.supportsDirectPlay && transcodingUrl != null) {
            val path = if (transcodingUrl.startsWith('/')) transcodingUrl else "/$transcodingUrl"
            return "$base$path".withApiKey(config.accessToken)
        }

        val mediaSourceId = mediaSource.id ?: itemId
        return "$base/Videos/$itemId/stream?Static=true&mediaSourceId=$mediaSourceId".withApiKey(config.accessToken)
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
            playedPercentage = userData?.playedPercentage,
            resumePositionTicks = userData?.playbackPositionTicks ?: 0L,
            cast = people.filter { it.type == "Actor" }.map { person ->
                EmbyCastMember(id = person.id.orEmpty(), name = person.name.orEmpty(), role = person.role)
            },
            childCount = childCount,
        )
    }

    private fun imageUrl(config: EmbySourceConfig, itemId: String, imageType: String, tag: String): String =
        "${config.serverUrl.trimEnd('/')}/Items/$itemId/Images/$imageType?tag=$tag".withApiKey(config.accessToken)

    private fun String.withApiKey(token: String): String {
        val separator = if (contains('?')) '&' else '?'
        return "$this${separator}api_key=$token"
    }
}
