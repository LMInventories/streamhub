package com.android.streamhub.feature.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class M3uRemoteDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun fetchChannels(playlistUrl: String): List<M3uChannel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(playlistUrl).build()
        val body = okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Failed to fetch M3U playlist: HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }
        M3uParser.parse(body)
    }
}
