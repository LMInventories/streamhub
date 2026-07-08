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

/**
 * Xtream Codes VOD (movies) browsing, alongside IptvBrowseRepository's live-channel browsing -
 * a separate repository rather than folding into that one since the two have no overlapping
 * queries (get_vod_categories/get_vod_streams vs get_live_categories/get_live_streams) and VOD
 * has no EPG concept at all.
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
}
