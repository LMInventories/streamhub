package com.android.streamhub.feature.jellyfin.data

import com.android.streamhub.core.common.search.FuzzyMatch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// 1 tick = 100ns (Jellyfin's .NET-derived convention throughout its API) - 600_000_000 ticks/min.
private const val TICKS_PER_MINUTE = 600_000_000L
private const val TICKS_PER_MS = 10_000L

// BaseItemDto.premiereDate is a bare java.time.LocalDateTime (no zone) - formatted once here so
// every screen that renders it gets the same "Aug 15, 2023" shape without re-implementing it.
private val PREMIERE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

// Matches WatchProgress.isNearlyComplete (core-common) - close enough to the end that the server
// should treat this the same as an explicit "mark watched" rather than just a resume point.
private const val NEARLY_COMPLETE_FRACTION = 0.92f

@Singleton
class JellyfinBrowseRepository @Inject constructor(
    private val jellyfin: Jellyfin,
    private val configRepository: JellyfinSourceConfigRepository,
    private val appSettingsRepository: JellyfinAppSettingsRepository,
) {
    // Cached per-config rather than rebuilt on every call - createApi() builds a fresh ApiClient
    // (own HTTP client/connection pool), so this avoids doing that on every single browse call.
    private var cachedConfig: JellyfinSourceConfig? = null
    private var cachedApi: ApiClient? = null

    private suspend fun apiOrNull(): ApiClient? {
        val config = configRepository.configFlow.first() ?: return null
        if (config != cachedConfig) {
            cachedApi = jellyfin.createApi(baseUrl = config.serverUrl, accessToken = config.accessToken)
            cachedConfig = config
        }
        return cachedApi
    }

    private fun currentUserId(): UUID? = cachedConfig?.userId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    /**
     * [includeAppHidden] is for the library-visibility settings screen, which needs to list every
     * library (including ones the user already hid via that same screen) to let them un-hide it -
     * every other caller wants the filtered list, which is why that's the default. Distinct from
     * the SDK call's own includeHidden param below (Jellyfin server-side "hidden from views"),
     * which this always leaves off - that's a different, server-managed concept.
     */
    suspend fun getLibraries(includeAppHidden: Boolean = false): List<JellyfinLibraryInfo> {
        val api = apiOrNull() ?: return emptyList()
        val libraries = api.userViewsApi.getUserViews(userId = currentUserId(), includeHidden = false)
            .content.items.mapNotNull { it.toLibraryInfo() }
        if (includeAppHidden) return libraries
        val hiddenIds = appSettingsRepository.settingsFlow.first().hiddenLibraryIds
        return libraries.filter { it.id !in hiddenIds }
    }

    suspend fun getLatestMedia(libraryId: String, limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.userLibraryApi.getLatestMedia(userId = currentUserId(), parentId = UUID.fromString(libraryId), limit = limit)
            .content.map { it.toItemInfo(api) }
    }

    suspend fun getResumeItems(limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.itemsApi.getResumeItems(
            userId = currentUserId(),
            limit = limit,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
        ).content.items.map { it.toItemInfo(api) }
    }

    suspend fun getNextUp(limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.tvShowsApi.getNextUp(userId = currentUserId(), limit = limit).content.items.map { it.toItemInfo(api) }
    }

    /** The one episode a series' own detail screen should surface as "Next Up" - null once the whole series is caught up, or if nothing's ever been started. */
    suspend fun getNextUp(seriesId: String): JellyfinItemInfo? {
        val api = apiOrNull() ?: return null
        return api.tvShowsApi.getNextUp(userId = currentUserId(), seriesId = UUID.fromString(seriesId), limit = 1)
            .content.items.firstOrNull()?.toItemInfo(api)
    }

    /**
     * Paginated library browse - libraryId identifies which library (Movies/TV Shows), itemType
     * picks which BaseItemKind to filter to since a "TV Shows" library's items() call needs
     * SERIES, not EPISODE.
     */
    suspend fun getItems(
        libraryId: String,
        itemType: JellyfinItemType,
        startIndex: Int,
        limit: Int,
        sortOption: JellyfinSortOption = JellyfinSortOption.NAME_ASC,
        favoritesOnly: Boolean = false,
        unwatchedOnly: Boolean = false,
    ): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        val kind = when (itemType) {
            JellyfinItemType.MOVIE -> BaseItemKind.MOVIE
            JellyfinItemType.SERIES -> BaseItemKind.SERIES
            else -> return emptyList()
        }
        val (sortBy, sortOrder) = sortOption.toSortByAndOrder()
        return api.itemsApi.getItems(
            userId = currentUserId(),
            parentId = UUID.fromString(libraryId),
            includeItemTypes = listOf(kind),
            recursive = true,
            startIndex = startIndex,
            limit = limit,
            sortBy = listOf(sortBy),
            sortOrder = listOf(sortOrder),
            isFavorite = if (favoritesOnly) true else null,
            isPlayed = if (unwatchedOnly) false else null,
        ).content.items.map { it.toItemInfo(api) }
    }

    /**
     * Server-side search (Jellyfin's ItemsApi takes a real searchTerm, unlike Xtream) across
     * movies/series/episodes, for Search. Jellyfin's own searchTerm match is a plain substring
     * match that doesn't treat spacing/hyphen differences as equivalent - "spider man" won't find
     * "Spider-Man" server-side. Sending just the query's first word casts a deliberately broader
     * net (an exact single-word match reliably finds titles Jellyfin's own matching would've
     * missed on the full phrase), then FuzzyMatch narrows the results back down client-side the
     * same way Search's other sources (EPG/VOD) do - so the server call's own limit is raised to
     * compensate for that broader, noisier net before the real limit is re-applied after filtering.
     */
    suspend fun search(query: String, limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        if (query.isBlank()) return emptyList()
        val broadTerm = query.trim().substringBefore(' ')
        val results = api.itemsApi.getItems(
            userId = currentUserId(),
            searchTerm = broadTerm,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
            recursive = true,
            limit = limit * 4,
            // MediaStreams isn't returned by default - needed here so toItemInfo can read the
            // video stream's height for the search result quality tag (4K/FHD/HD/SD).
            fields = listOf(ItemFields.MEDIA_STREAMS),
        ).content.items.map { it.toItemInfo(api) }
        return results.filter { FuzzyMatch.matches(it.name, query) }.take(limit)
    }

    /** Favorites can be a mix of movies and series (unlike getItems, which is scoped to one library/kind), and span every library rather than one - so this is its own call rather than getItems with an extra flag. */
    suspend fun getFavorites(startIndex: Int, limit: Int): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.itemsApi.getItems(
            userId = currentUserId(),
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            recursive = true,
            isFavorite = true,
            startIndex = startIndex,
            limit = limit,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        ).content.items.map { it.toItemInfo(api) }
    }

    /** Returns the new favorite state on success, null if the call failed (caller should leave the UI state unchanged). */
    suspend fun toggleFavorite(itemId: String, currentlyFavorite: Boolean): Boolean? {
        val api = apiOrNull() ?: return null
        val uuid = UUID.fromString(itemId)
        return runCatching {
            val result = if (currentlyFavorite) {
                api.userLibraryApi.unmarkFavoriteItem(itemId = uuid, userId = currentUserId())
            } else {
                api.userLibraryApi.markFavoriteItem(itemId = uuid, userId = currentUserId())
            }
            result.content.isFavorite
        }.getOrNull()
    }

    private fun JellyfinSortOption.toSortByAndOrder(): Pair<ItemSortBy, SortOrder> = when (this) {
        JellyfinSortOption.NAME_ASC -> ItemSortBy.SORT_NAME to SortOrder.ASCENDING
        JellyfinSortOption.NAME_DESC -> ItemSortBy.SORT_NAME to SortOrder.DESCENDING
        JellyfinSortOption.DATE_ADDED_NEWEST -> ItemSortBy.DATE_CREATED to SortOrder.DESCENDING
        JellyfinSortOption.RATING_HIGHEST -> ItemSortBy.COMMUNITY_RATING to SortOrder.DESCENDING
        JellyfinSortOption.RELEASE_DATE_NEWEST -> ItemSortBy.PREMIERE_DATE to SortOrder.DESCENDING
    }

    // Unlike the plural /Items list endpoint (itemsApi.getItems, used by search() above), this
    // single-item endpoint doesn't need an explicit `fields` request for MediaStreams - Jellyfin
    // returns a full detail view (streams, people, overview, etc.) here by default, which is
    // exactly why every official client uses this same endpoint to build its own detail screen.
    // MediaSources (needed for the Version picker's alternate-encode list) is requested explicitly
    // anyway - unlike MediaStreams, it's not consistently guaranteed part of that default view
    // across server versions, and asking for a field that's already included by default is a no-op.
    suspend fun getItem(itemId: String): JellyfinItemInfo? {
        val api = apiOrNull() ?: return null
        return runCatching {
            api.userLibraryApi.getItem(
                itemId = UUID.fromString(itemId),
                userId = currentUserId(),
                fields = listOf(ItemFields.MEDIA_SOURCES),
            ).content
        }.getOrNull()?.toItemInfo(api)
    }

    /** Returns the new played state on success, null if the call failed (caller should leave the UI state unchanged) - same "optimistic, reconcile with the server" shape as toggleFavorite above. */
    suspend fun toggleWatched(itemId: String, currentlyPlayed: Boolean): Boolean? {
        val api = apiOrNull() ?: return null
        val uuid = UUID.fromString(itemId)
        return runCatching {
            val result = if (currentlyPlayed) {
                api.playStateApi.markUnplayedItem(itemId = uuid, userId = currentUserId())
            } else {
                api.playStateApi.markPlayedItem(itemId = uuid, userId = currentUserId())
            }
            result.content.played
        }.getOrNull()
    }

    /**
     * Playback lifecycle reporting via Jellyfin's Sessions API - this is what makes a native
     * Jellyfin app (or the web UI) see the same "Continue Watching"/resume-position/watched state
     * this app produces, instead of that only ever living in this app's own local
     * WatchProgressRepository (which was all playback ever updated before this). The three calls
     * mirror what every official Jellyfin client does around a playback session: report start
     * once, progress periodically (also covers pause, via isPaused), and stopped once when the
     * player screen is torn down. Best-effort/fire-and-forget - a failed report here should never
     * interrupt playback, so every call swallows its own failure.
     */
    suspend fun reportPlaybackStart(itemId: String) {
        val api = apiOrNull() ?: return
        val uuid = UUID.fromString(itemId)
        runCatching {
            api.playStateApi.reportPlaybackStart(
                data = PlaybackStartInfo(
                    itemId = uuid,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_PLAY,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }
    }

    suspend fun reportPlaybackProgress(itemId: String, positionMs: Long, isPaused: Boolean) {
        val api = apiOrNull() ?: return
        val uuid = UUID.fromString(itemId)
        runCatching {
            api.playStateApi.reportPlaybackProgress(
                data = PlaybackProgressInfo(
                    itemId = uuid,
                    positionTicks = positionMs * TICKS_PER_MS,
                    isPaused = isPaused,
                    canSeek = true,
                    isMuted = false,
                    playMethod = PlayMethod.DIRECT_PLAY,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }
    }

    /**
     * [positionMs]/[durationMs] decide whether this also explicitly marks the item played -
     * relying on the server's own completion heuristic here would mean this app's "nearly
     * complete" cutoff (see WatchProgress.isNearlyComplete in core-common) could disagree with
     * whatever the server assumes, so it's made explicit here to match exactly.
     */
    suspend fun reportPlaybackStopped(itemId: String, positionMs: Long, durationMs: Long) {
        val api = apiOrNull() ?: return
        val uuid = UUID.fromString(itemId)
        val positionTicks = positionMs * TICKS_PER_MS
        runCatching {
            api.playStateApi.reportPlaybackStopped(
                data = PlaybackStopInfo(itemId = uuid, positionTicks = positionTicks, failed = false),
            )
        }
        if (durationMs > 0 && positionMs.toFloat() / durationMs.toFloat() > NEARLY_COMPLETE_FRACTION) {
            runCatching { api.playStateApi.markPlayedItem(itemId = uuid, userId = currentUserId()) }
        }
    }

    suspend fun getSeasons(seriesId: String): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.tvShowsApi.getSeasons(seriesId = UUID.fromString(seriesId), userId = currentUserId())
            .content.items.map { it.toItemInfo(api) }
    }

    suspend fun getEpisodes(seriesId: String, seasonId: String): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.tvShowsApi.getEpisodes(seriesId = UUID.fromString(seriesId), seasonId = UUID.fromString(seasonId), userId = currentUserId())
            .content.items.map { it.toItemInfo(api) }
    }

    /** Recommendations for a series' own detail screen "More Like This" row - server-side similarity, same categories/genres-driven logic every official Jellyfin client uses. */
    suspend fun getSimilarShows(seriesId: String, limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.libraryApi.getSimilarShows(itemId = UUID.fromString(seriesId), userId = currentUserId(), limit = limit)
            .content.items.map { it.toItemInfo(api) }
    }

    /**
     * A direct-play stream URL - ExoPlayer makes its own separate HTTP request outside the SDK's
     * client, so the access token has to travel as an api_key query param rather than the SDK's
     * usual Authorization header (a documented, if less secure, alternative Jellyfin's own server
     * supports specifically for this kind of raw-URL playback scenario). Same reasoning applies
     * to every image URL below - Coil requests those separately too.
     *
     * maxStreamingBitrateMbps only ever narrows things - passing it as PlaybackInfoDto.maxStreamingBitrate
     * lets the server decide whether the source is already under the cap (direct play still
     * happens exactly as before) or needs transcoding to fit it, in which case the response's
     * MediaSourceInfo carries a ready-to-use TranscodingUrl instead of us building the direct-play
     * URL ourselves - deliberately not building a DeviceProfile to go with it, so the server falls
     * back to a conservative default rather than us guessing this device's real decode support.
     */
    suspend fun getStreamUrl(itemId: String, mediaSourceId: String? = null): String? {
        val api = apiOrNull() ?: return null
        val uuid = UUID.fromString(itemId)
        val maxBitrateBps = appSettingsRepository.settingsFlow.first().maxStreamingBitrateMbps?.let { it * 1_000_000 }
        val mediaSources = runCatching {
            api.mediaInfoApi.getPostedPlaybackInfo(
                itemId = uuid,
                data = PlaybackInfoDto(userId = currentUserId(), maxStreamingBitrate = maxBitrateBps),
            ).content
        }.getOrNull()?.mediaSources.orEmpty()
        // An explicit per-item version choice from the detail page's Version picker (if any) selects
        // a specific entry out of this same list PlaybackInfo already returns for every version -
        // falls back to the first (server's own default) when no choice was made, same as before
        // this picker existed.
        val mediaSource = mediaSources.firstOrNull { it.id == mediaSourceId } ?: mediaSources.firstOrNull()

        val transcodingUrl = mediaSource?.transcodingUrl
        if (mediaSource != null && !mediaSource.supportsDirectPlay && transcodingUrl != null) {
            val base = cachedConfig?.serverUrl.orEmpty().trimEnd('/')
            val path = if (transcodingUrl.startsWith('/')) transcodingUrl else "/$transcodingUrl"
            return "$base$path".withApiKey()
        }

        val url = api.videosApi.getVideoStreamUrl(
            itemId = uuid,
            static = true,
            mediaSourceId = mediaSource?.id,
            container = mediaSource?.container,
        )
        return url.withApiKey()
    }

    /**
     * Earliest "Outro" Media Segment for this item (10.10+ servers that have run segment analysis
     * for it), ms from item start - null on older servers, unanalyzed content, or any failure; the
     * Next Episode prompt falls back cleanly to its fixed-lead-time behavior in every one of those
     * cases. Chapters (BaseItemDto.chapters) are deliberately not used as a fallback signal here -
     * unlike Media Segments they carry no type info at all, so there's no reliable "this chapter is
     * the credits" marker to build one from.
     */
    suspend fun getOutroStartMs(itemId: String): Long? {
        val api = apiOrNull() ?: return null
        return runCatching {
            api.mediaSegmentsApi.getItemSegments(
                itemId = UUID.fromString(itemId),
                includeSegmentTypes = listOf(MediaSegmentType.OUTRO),
            ).content.items
        }.getOrNull()
            ?.minByOrNull { it.startTicks }
            ?.let { it.startTicks / TICKS_PER_MS }
    }

    private fun String.withApiKey(): String {
        val token = cachedConfig?.accessToken ?: return this
        val separator = if (contains('?')) '&' else '?'
        return "$this${separator}api_key=$token"
    }

    private fun BaseItemDto.toLibraryInfo(): JellyfinLibraryInfo? {
        val libraryType = when (collectionType) {
            CollectionType.MOVIES -> JellyfinLibraryType.MOVIES
            CollectionType.TVSHOWS -> JellyfinLibraryType.TV_SHOWS
            else -> return null
        }
        return JellyfinLibraryInfo(id = id.toString(), name = name.orEmpty(), type = libraryType)
    }

    private fun BaseItemDto.toItemInfo(api: ApiClient): JellyfinItemInfo {
        val itemType = when (type) {
            BaseItemKind.MOVIE -> JellyfinItemType.MOVIE
            BaseItemKind.SERIES -> JellyfinItemType.SERIES
            BaseItemKind.SEASON -> JellyfinItemType.SEASON
            BaseItemKind.EPISODE -> JellyfinItemType.EPISODE
            else -> JellyfinItemType.OTHER
        }
        // An episode's own "Primary" image is a screen-grab by Jellyfin's own data model, not a
        // poster - correct for a 16:9 scene thumbnail, but every place this app renders
        // primaryImageUrl does so as a 2:3 poster (Continue Watching/Next Up rows, poster grids,
        // detail screens), where showing that screen-grab reads as broken/wrong art even though
        // it's technically the right field. BaseItemDto separately carries seriesPrimaryImageTag
        // (the actual series poster's tag) for exactly this - official Jellyfin clients use it for
        // episode tiles in poster-shaped contexts, which this now matches. Falls back to the
        // episode's own primary only if the series tag is somehow unavailable.
        val primaryTag = imageTags?.get(ImageType.PRIMARY)
        // Copied to locals - seriesId/seriesPrimaryImageTag are declared in the SDK module, so
        // Kotlin won't smart-cast them from a null-check straight to non-null usage below (only
        // works for vals in the same module); a local val doesn't have that restriction.
        val episodeSeriesId = seriesId
        val episodeSeriesPrimaryTag = seriesPrimaryImageTag
        val primaryImageUrl = when {
            itemType == JellyfinItemType.EPISODE && episodeSeriesId != null && episodeSeriesPrimaryTag != null ->
                api.imageApi.getItemImageUrl(itemId = episodeSeriesId, imageType = ImageType.PRIMARY, tag = episodeSeriesPrimaryTag).withApiKey()
            primaryTag != null -> api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, tag = primaryTag).withApiKey()
            else -> null
        }
        val backdropImageUrl = backdropImageTags?.firstOrNull()
            ?.let { api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.BACKDROP, tag = it).withApiKey() }
        // Bypasses the series-poster override above - this is the episode's own scene-grab, for
        // contexts (episode detail hero) that want the real per-episode thumbnail, not the series
        // poster primaryImageUrl deliberately substitutes in for poster-shaped grids.
        val episodeThumbnailUrl = primaryTag?.takeIf { itemType == JellyfinItemType.EPISODE }
            ?.let { api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, tag = it).withApiKey() }
        // Movies/series carry their own transparent wordmark; episodes don't (no series-level
        // fallback here the way primaryImageUrl has one above) - the preview panel falls back to
        // a plain text title when this is null, which is already the common case for episodes.
        val logoImageUrl = imageTags?.get(ImageType.LOGO)
            ?.let { tag -> api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.LOGO, tag = tag).withApiKey() }
        // displayTitle is the server's own pre-formatted summary of a stream (e.g. "1080p H264",
        // "5.1 English AC3 - Default") - reusing it instead of hand-building one from individual
        // codec/channel/language fields matches what every official Jellyfin client already shows.
        val videoLabel = mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.displayTitle
        val audioStreams = mediaStreams?.filter { it.type == MediaStreamType.AUDIO }.orEmpty()
        val audioLabel = (audioStreams.firstOrNull { it.isDefault == true } ?: audioStreams.firstOrNull())?.displayTitle
        val subtitleTracks = mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }.orEmpty()
            .map { stream ->
                JellyfinSubtitleTrackInfo(
                    index = stream.index ?: 0,
                    label = stream.displayTitle ?: stream.language ?: "Subtitle",
                    language = stream.language,
                )
            }
        val audioTracks = audioStreams.map { stream ->
            JellyfinAudioTrackInfo(
                index = stream.index ?: 0,
                label = stream.displayTitle ?: stream.language ?: "Audio",
                language = stream.language,
                isDefault = stream.isDefault == true,
            )
        }
        // Only worth surfacing as a picker when there's an actual choice - a single-version item
        // (the overwhelming majority) just keeps using the plain read-only Video row instead.
        val videoVersions = mediaSources.orEmpty()
            .takeIf { it.size > 1 }
            ?.mapIndexed { index, source ->
                JellyfinVersionInfo(
                    id = source.id ?: id.toString(),
                    label = source.name?.takeIf { it.isNotBlank() } ?: "Version ${index + 1}",
                )
            }
            .orEmpty()
        return JellyfinItemInfo(
            id = id.toString(),
            name = name.orEmpty(),
            type = itemType,
            overview = overview,
            productionYear = productionYear,
            communityRating = communityRating,
            genres = genres.orEmpty(),
            runtimeMinutes = runTimeTicks?.let { (it / TICKS_PER_MINUTE).toInt() },
            primaryImageUrl = primaryImageUrl,
            backdropImageUrl = backdropImageUrl,
            episodeThumbnailUrl = episodeThumbnailUrl,
            premiereDateLabel = premiereDate?.format(PREMIERE_DATE_FORMATTER),
            childCount = childCount,
            unplayedItemCount = userData?.unplayedItemCount,
            logoImageUrl = logoImageUrl,
            videoWidth = mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.width,
            videoHeight = mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.height,
            videoLabel = videoLabel,
            audioLabel = audioLabel,
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            videoVersions = videoVersions,
            seriesId = seriesId?.toString(),
            seriesName = seriesName,
            seasonId = seasonId?.toString(),
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
            isFavorite = userData?.isFavorite ?: false,
            isPlayed = userData?.played ?: false,
            playedPercentage = userData?.playedPercentage?.toFloat(),
            resumePositionTicks = userData?.playbackPositionTicks ?: 0L,
            cast = people.orEmpty().toCastMembers(api, PersonKind.ACTOR),
            // An episode's own people payload doesn't repeat the series' regular cast (that's
            // `cast` above, populated for movies/series) - just its own director(s) and any
            // episode-specific guest stars.
            crew = people.orEmpty().toCastMembers(api, PersonKind.DIRECTOR),
            guestStars = people.orEmpty().toCastMembers(api, PersonKind.GUEST_STAR),
            tags = tags.orEmpty(),
            studios = studios.orEmpty().mapNotNull { it.name },
            externalLinks = externalUrls.orEmpty().mapNotNull { link -> link.name?.let { name -> link.url?.let { url -> name to url } } },
        )
    }

    private fun List<BaseItemPerson>.toCastMembers(api: ApiClient, kind: PersonKind): List<JellyfinCastMember> =
        filter { it.type == kind }.map { person ->
            JellyfinCastMember(
                id = person.id.toString(),
                name = person.name.orEmpty(),
                role = person.role,
                imageUrl = person.primaryImageTag?.let { tag ->
                    api.imageApi.getPersonImageUrl(name = person.name.orEmpty(), imageType = ImageType.PRIMARY, tag = tag).withApiKey()
                },
            )
        }
}
