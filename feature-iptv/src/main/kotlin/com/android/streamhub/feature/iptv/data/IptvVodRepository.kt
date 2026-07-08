package com.android.streamhub.feature.iptv.data

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class VodCategoryInfo(val id: String, val name: String)

data class VodMovieInfo(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val streamUrl: String,
)

data class VodShowInfo(
    val id: String,
    val name: String,
    val posterUrl: String?,
)

data class VodEpisodeInfo(
    // Fully namespaced playback id, ready for Route.playerRoute/Route.vodItemDetailRoute.
    val playbackId: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val plot: String? = null,
)

data class VodSeriesDetailInfo(
    val name: String,
    val posterUrl: String?,
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val rating: String?,
    val episodes: List<VodEpisodeInfo>,
)

/**
 * Detail info for either a movie or a single episode (shared shape since the fields mostly
 * overlap) - seriesName/seasonNumber/episodeNumber are only populated for episodes. Xtream only
 * reports one representative audio/video ffprobe stream here, not an enumerable list of every
 * track in a multi-audio file, so audioTag/videoTag are informational display text, not a real
 * selectable list - genuine track switching happens in the player itself once it has actually
 * opened the stream.
 */
data class VodDetailInfo(
    val playbackId: String,
    val streamUrl: String,
    val title: String,
    val posterUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val duration: String?,
    val audioTag: String?,
    val videoTag: String?,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

/**
 * Xtream Codes VOD (movies + TV shows) browsing, alongside IptvBrowseRepository's live-channel
 * browsing - a separate repository rather than folding into that one since the two have no
 * overlapping queries and VOD has no EPG concept at all.
 *
 * M3U sources have no reliable, standardized way to distinguish VOD from live channels, so VOD
 * browsing is Xtream-only for now - callers should check [isSupported] before showing this tab's
 * content as available.
 */
@Singleton
class IptvVodRepository @Inject constructor(
    private val configRepository: IptvSourceConfigRepository,
    private val xtreamRemoteDataSource: XtreamRemoteDataSource,
) {
    suspend fun isSupported(): Boolean = configRepository.configFlow.first() is IptvSourceConfig.Xtream

    suspend fun getCategories(): List<VodCategoryInfo> {
        val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream ?: return emptyList()
        return xtreamRemoteDataSource.getVodCategories(config).map { VodCategoryInfo(it.categoryId, it.categoryName) }
    }

    suspend fun getMovies(categoryId: String): List<VodMovieInfo> {
        val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream ?: return emptyList()
        return xtreamRemoteDataSource.getVodStreams(config, categoryId).map {
            VodMovieInfo(
                // Must match IptvMediaSource.vodPlaybackId's scheme - the id navigated with here
                // is what PlayerViewModel/resolvePlayback looks up later.
                id = vodPlaybackId(it.streamId),
                name = it.name,
                posterUrl = it.streamIcon,
                streamUrl = config.vodStreamUrl(it.streamId, it.containerExtension ?: "mp4"),
            )
        }
    }

    suspend fun getMovieDetail(playbackId: String): VodDetailInfo? {
        val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream ?: return null
        val streamId = playbackId.removePrefix("vod:")
        val response = xtreamRemoteDataSource.getVodInfo(config, streamId)
        val info = response.info
        return VodDetailInfo(
            playbackId = playbackId,
            streamUrl = config.vodStreamUrl(streamId),
            title = response.movieData?.name?.takeIf(String::isNotBlank) ?: "Movie",
            posterUrl = info?.movieImage,
            plot = info?.plot?.takeIf(String::isNotBlank),
            cast = info?.cast?.takeIf(String::isNotBlank),
            director = info?.director?.takeIf(String::isNotBlank),
            genre = info?.genre?.takeIf(String::isNotBlank),
            releaseDate = info?.releaseDate?.takeIf(String::isNotBlank),
            rating = info?.rating?.takeIf(String::isNotBlank),
            duration = info?.duration?.takeIf(String::isNotBlank),
            audioTag = info?.audio?.describe(),
            videoTag = info?.video?.describe(),
        )
    }

    suspend fun getSeriesCategories(): List<VodCategoryInfo> {
        val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream ?: return emptyList()
        return xtreamRemoteDataSource.getSeriesCategories(config).map { VodCategoryInfo(it.categoryId, it.categoryName) }
    }

    suspend fun getShows(categoryId: String): List<VodShowInfo> {
        val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream ?: return emptyList()
        return xtreamRemoteDataSource.getSeries(config, categoryId).map { VodShowInfo(it.seriesId, it.name, it.cover) }
    }

    suspend fun getSeriesDetail(seriesId: String): VodSeriesDetailInfo? {
        val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream ?: return null
        val response = xtreamRemoteDataSource.getSeriesInfo(config, seriesId)
        val info = response.info
        val episodes = response.episodes.values.flatten()
            .mapNotNull { episode ->
                val season = episode.season.toIntOrNull() ?: return@mapNotNull null
                val episodeNum = episode.episodeNum.toIntOrNull() ?: 0
                VodEpisodeInfo(
                    playbackId = episodePlaybackId(seriesId, episode.id),
                    title = episode.title?.takeIf(String::isNotBlank) ?: "Episode $episodeNum",
                    seasonNumber = season,
                    episodeNumber = episodeNum,
                    plot = episode.info?.plot?.takeIf(String::isNotBlank),
                )
            }
            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        return VodSeriesDetailInfo(
            name = info?.name?.takeIf(String::isNotBlank) ?: "Show",
            posterUrl = info?.cover,
            plot = info?.plot?.takeIf(String::isNotBlank),
            cast = info?.cast?.takeIf(String::isNotBlank),
            genre = info?.genre?.takeIf(String::isNotBlank),
            rating = info?.rating?.takeIf(String::isNotBlank),
            episodes = episodes,
        )
    }

    suspend fun getEpisodeDetail(playbackId: String): VodDetailInfo? {
        val (seriesId, episodeId) = parseEpisodePlaybackId(playbackId) ?: return null
        val config = configRepository.configFlow.first() as? IptvSourceConfig.Xtream ?: return null
        val response = xtreamRemoteDataSource.getSeriesInfo(config, seriesId)
        val episode = response.episodes.values.flatten().firstOrNull { it.id == episodeId } ?: return null
        val episodeInfo = episode.info
        val season = episode.season.toIntOrNull()
        val episodeNum = episode.episodeNum.toIntOrNull()
        return VodDetailInfo(
            playbackId = playbackId,
            streamUrl = config.vodStreamUrl(episodeId, episode.containerExtension ?: "mp4"),
            title = episode.title?.takeIf(String::isNotBlank) ?: "Episode",
            posterUrl = episodeInfo?.movieImage ?: response.info?.cover,
            plot = episodeInfo?.plot?.takeIf(String::isNotBlank),
            cast = response.info?.cast?.takeIf(String::isNotBlank),
            director = response.info?.director?.takeIf(String::isNotBlank),
            genre = response.info?.genre?.takeIf(String::isNotBlank),
            releaseDate = null,
            rating = episodeInfo?.rating?.takeIf(String::isNotBlank),
            duration = episodeInfo?.duration?.takeIf(String::isNotBlank),
            audioTag = episodeInfo?.audio?.describe(),
            videoTag = episodeInfo?.video?.describe(),
            seriesName = response.info?.name?.takeIf(String::isNotBlank),
            seasonNumber = season,
            episodeNumber = episodeNum,
        )
    }
}

private fun XtreamStreamProbe.describe(): String? {
    val codec = codecName?.uppercase()
    val language = tags?.language?.takeIf(String::isNotBlank)
    return when {
        codec != null && language != null -> "$codec ($language)"
        codec != null -> codec
        language != null -> language
        else -> null
    }
}
