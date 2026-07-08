package com.android.streamhub.feature.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class M3uRemoteDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun fetchChannels(playlistUrl: String): List<M3uChannel> =
        fetchText(playlistUrl)?.let { M3uParser.parse(it) }.orEmpty()

    /**
     * channelId (tvg-id) -> its programmes. Returns empty if no EPG URL is configured or it fails
     * to fetch. Streams straight into the SAX parser rather than buffering the whole body as a
     * String first (as fetchText/response.body.string() would) - some XMLTV guides run into the
     * hundreds of MB and reliably OOM a real device's heap if fully materialized at once.
     */
    suspend fun fetchEpg(epgUrl: String): Map<String, List<EpgProgram>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(epgUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Failed to fetch $epgUrl: HTTP ${response.code}" }
                val body = checkNotNull(response.body) { "Empty response body" }
                XmlTvParser.parse(body.byteStream())
            }
        }.getOrDefault(emptyList())
            .groupBy(keySelector = { it.channelId }, valueTransform = { it.program })
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Failed to fetch $url: HTTP ${response.code}" }
            response.body?.string()
        }
    }
}
