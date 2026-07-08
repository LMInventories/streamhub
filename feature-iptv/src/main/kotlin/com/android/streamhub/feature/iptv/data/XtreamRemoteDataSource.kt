package com.android.streamhub.feature.iptv.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * The user's Xtream server URL is only known at runtime (entered in Settings), so the Retrofit
 * instance can't be a single Hilt-provided singleton with a fixed base URL - build one per
 * base URL instead, cached so repeat calls against the same server don't rebuild it.
 */
class XtreamRemoteDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val apiCache = mutableMapOf<String, XtreamApi>()

    private fun apiFor(baseUrl: String): XtreamApi = apiCache.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(XtreamApi::class.java)
    }

    suspend fun getLiveStreams(config: IptvSourceConfig.Xtream, categoryId: String? = null): List<XtreamLiveStream> =
        apiFor(config.baseUrl).getLiveStreams(config.username, config.password, categoryId = categoryId)

    suspend fun getLiveCategories(config: IptvSourceConfig.Xtream): List<XtreamLiveCategory> =
        apiFor(config.baseUrl).getLiveCategories(config.username, config.password)

    suspend fun getShortEpg(config: IptvSourceConfig.Xtream, streamId: String): List<EpgProgram> =
        apiFor(config.baseUrl)
            .getShortEpg(config.username, config.password, streamId)
            .epgListings
            .mapNotNull { it.toEpgProgram() }

    suspend fun getVodCategories(config: IptvSourceConfig.Xtream): List<XtreamVodCategory> =
        apiFor(config.baseUrl).getVodCategories(config.username, config.password)

    suspend fun getVodStreams(config: IptvSourceConfig.Xtream, categoryId: String? = null): List<XtreamVodStream> =
        apiFor(config.baseUrl).getVodStreams(config.username, config.password, categoryId = categoryId)
}
