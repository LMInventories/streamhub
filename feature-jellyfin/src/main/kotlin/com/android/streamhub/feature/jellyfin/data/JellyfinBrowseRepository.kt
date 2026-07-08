package com.android.streamhub.feature.jellyfin.data

import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SortOrder
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// 1 tick = 100ns (Jellyfin's .NET-derived convention throughout its API) - 600_000_000 ticks/min.
private const val TICKS_PER_MINUTE = 600_000_000L

@Singleton
class JellyfinBrowseRepository @Inject constructor(
    private val jellyfin: Jellyfin,
    private val configRepository: JellyfinSourceConfigRepository,
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

    suspend fun getLibraries(): List<JellyfinLibraryInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.userViewApi.getUserViews(userId = currentUserId(), includeHidden = false)
            .content.items.mapNotNull { it.toLibraryInfo() }
    }

    suspend fun getLatestMedia(libraryId: String, limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.libraryApi.getLatestMedia(userId = currentUserId(), parentId = UUID.fromString(libraryId), limit = limit)
            .content.map { it.toItemInfo(api) }
    }

    suspend fun getResumeItems(limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.libraryApi.getResumeItems(
            userId = currentUserId(),
            limit = limit,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
        ).content.items.map { it.toItemInfo(api) }
    }

    suspend fun getNextUp(limit: Int = 20): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.showApi.getNextUp(userId = currentUserId(), limit = limit).content.items.map { it.toItemInfo(api) }
    }

    /** Paginated library browse - libraryId identifies which library (Movies/TV Shows), itemType picks which BaseItemKind to filter to since a "TV Shows" library's items() call needs SERIES, not EPISODE. */
    suspend fun getItems(libraryId: String, itemType: JellyfinItemType, startIndex: Int, limit: Int): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        val kind = when (itemType) {
            JellyfinItemType.MOVIE -> BaseItemKind.MOVIE
            JellyfinItemType.SERIES -> BaseItemKind.SERIES
            else -> return emptyList()
        }
        return api.libraryApi.getItems(
            userId = currentUserId(),
            parentId = UUID.fromString(libraryId),
            includeItemTypes = listOf(kind),
            recursive = true,
            startIndex = startIndex,
            limit = limit,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        ).content.items.map { it.toItemInfo(api) }
    }

    suspend fun getItem(itemId: String): JellyfinItemInfo? {
        val api = apiOrNull() ?: return null
        return runCatching { api.libraryApi.getItem(itemId = UUID.fromString(itemId), userId = currentUserId()).content }
            .getOrNull()?.toItemInfo(api)
    }

    suspend fun getSeasons(seriesId: String): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.showApi.getSeasons(seriesId = UUID.fromString(seriesId), userId = currentUserId())
            .content.items.map { it.toItemInfo(api) }
    }

    suspend fun getEpisodes(seriesId: String, seasonId: String): List<JellyfinItemInfo> {
        val api = apiOrNull() ?: return emptyList()
        return api.showApi.getEpisodes(seriesId = UUID.fromString(seriesId), seasonId = UUID.fromString(seasonId), userId = currentUserId())
            .content.items.map { it.toItemInfo(api) }
    }

    /**
     * A direct-play stream URL - ExoPlayer makes its own separate HTTP request outside the SDK's
     * client, so the access token has to travel as an api_key query param rather than the SDK's
     * usual Authorization header (a documented, if less secure, alternative Jellyfin's own server
     * supports specifically for this kind of raw-URL playback scenario). Same reasoning applies
     * to every image URL below - Coil requests those separately too.
     */
    suspend fun getStreamUrl(itemId: String): String? {
        val api = apiOrNull() ?: return null
        val uuid = UUID.fromString(itemId)
        val mediaSource = runCatching { api.mediaInfoApi.getPlaybackInfo(itemId = uuid, userId = currentUserId()).content }
            .getOrNull()?.mediaSources?.firstOrNull()
        val url = api.videoApi.getVideoStreamUrl(
            itemId = uuid,
            static = true,
            mediaSourceId = mediaSource?.id,
            container = mediaSource?.container,
        )
        return url.withApiKey()
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
        val primaryTag = imageTags?.get(ImageType.PRIMARY)
        val primaryImageUrl = primaryTag
            ?.let { api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, tag = it).withApiKey() }
        val backdropImageUrl = backdropImageTags?.firstOrNull()
            ?.let { api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.BACKDROP, tag = it).withApiKey() }
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
            seriesId = seriesId?.toString(),
            seriesName = seriesName,
            seasonId = seasonId?.toString(),
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
            isFavorite = userData?.isFavorite ?: false,
            playedPercentage = userData?.playedPercentage?.toFloat(),
            resumePositionTicks = userData?.playbackPositionTicks ?: 0L,
            cast = people.orEmpty()
                .filter { it.type == PersonKind.ACTOR }
                .map { person ->
                    JellyfinCastMember(
                        id = person.id.toString(),
                        name = person.name.orEmpty(),
                        role = person.role,
                        imageUrl = person.primaryImageTag?.let { tag ->
                            api.imageApi.getPersonImageUrl(name = person.name.orEmpty(), imageType = ImageType.PRIMARY, tag = tag).withApiKey()
                        },
                    )
                },
        )
    }
}
