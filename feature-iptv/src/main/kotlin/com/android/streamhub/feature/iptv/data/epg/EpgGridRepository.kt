package com.android.streamhub.feature.iptv.data.epg

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import com.android.streamhub.feature.iptv.data.IptvSourceConfig
import com.android.streamhub.feature.iptv.data.IptvSourceConfigRepository
import com.android.streamhub.feature.iptv.data.XmlTvParser
import com.android.streamhub.feature.iptv.data.XmlTvProgramme
import com.android.streamhub.feature.iptv.data.epgKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xml.sax.SAXException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class EpgRefreshResult {
    data object UpToDate : EpgRefreshResult()
    data class Fetched(val programmeCount: Int, val channelCount: Int) : EpgRefreshResult()
    /** [hasCachedData] tells the caller whether it's still safe to show a (stale) previously-fetched grid despite this failure. */
    data class Failed(val reason: String, val hasCachedData: Boolean) : EpgRefreshResult()
    data object NotConfigured : EpgRefreshResult()
}

/**
 * Bulk EPG for the multi-day grid - deliberately separate from IptvBrowseRepository's
 * per-channel on-demand now/next (which stays on Xtream's lighter get_short_epg call for that
 * use case). A 7-day guide needs the full XMLTV dump either way - Xtream serves one at
 * xmltv.php the same as a standalone M3U+EPG-URL setup - so both source types funnel through
 * the same parser and the same Room-backed cache here.
 */
@Singleton
class EpgGridRepository @Inject constructor(
    private val configRepository: IptvSourceConfigRepository,
    private val dao: EpgDao,
    private val okHttpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>,
) {
    private val lastRefreshedKey = longPreferencesKey("epg_grid_last_refreshed_epoch_seconds")

    // A bit under 24h so a daily refresh doesn't creep later and later each day.
    private val refreshIntervalSeconds = TimeUnit.HOURS.toSeconds(20)

    /**
     * Fetches and stores the full guide if the cache is missing or stale. Every failure mode
     * (no EPG configured, download failed, response wasn't valid XMLTV, valid XML but zero
     * programmes in it) is reported distinctly rather than collapsed into a plain false/true -
     * this used to fail completely silently, which made a real bug (an OutOfMemoryError from
     * buffering a 280MB+ guide into one String, fixed here by streaming straight into the SAX
     * parser instead) impossible to diagnose from a bug report alone. [onProgress] is only
     * invoked while an actual download is happening - if the cache is still fresh, it's never
     * called, so callers can use "did I get any progress calls" to decide whether to show a bar.
     * [forceRefresh] skips the freshness check entirely - used by "Update Playlist" in Settings.
     */
    suspend fun ensureFresh(forceRefresh: Boolean = false, onProgress: (Float) -> Unit = {}): EpgRefreshResult = withContext(Dispatchers.IO) {
        val config = configRepository.configFlow.first() ?: return@withContext EpgRefreshResult.NotConfigured
        val xmltvUrl = xmltvUrlFor(config) ?: return@withContext EpgRefreshResult.NotConfigured

        val lastRefreshed = dataStore.data.map { it[lastRefreshedKey] ?: 0L }.first()
        val now = Instant.now().epochSecond
        if (!forceRefresh && now - lastRefreshed < refreshIntervalSeconds && dao.count() > 0) {
            return@withContext EpgRefreshResult.UpToDate
        }

        val fetchResult = fetchAndParse(xmltvUrl, onProgress)
        val programmes = fetchResult.getOrElse { throwable ->
            return@withContext EpgRefreshResult.Failed(
                reason = describeFetchFailure(throwable),
                hasCachedData = dao.count() > 0,
            )
        }
        if (programmes.isEmpty()) {
            return@withContext EpgRefreshResult.Failed(
                reason = "Guide downloaded successfully but contained no programme entries in the relevant date range",
                hasCachedData = dao.count() > 0,
            )
        }

        dao.clearAll()
        dao.insertAll(
            programmes.map {
                ProgrammeEntity(
                    channelId = it.channelId,
                    startAtEpochSeconds = it.program.startAt.epochSecond,
                    endAtEpochSeconds = it.program.endAt.epochSecond,
                    title = it.program.title,
                    description = it.program.description,
                )
            },
        )
        dataStore.edit { it[lastRefreshedKey] = now }
        EpgRefreshResult.Fetched(programmeCount = programmes.size, channelCount = programmes.map { it.channelId }.distinct().size)
    }

    /**
     * Real-world Xtream panels are inconsistent about whether xmltv.php's <programme channel="">
     * actually matches epg_channel_id or falls back to stream_id - rather than betting on one
     * specific convention, this queries for both candidate ids per channel and, per channel,
     * prefers whichever one actually had matching rows.
     */
    suspend fun getGrid(channels: List<IptvChannelInfo>, from: Instant, to: Instant): Map<String, List<EpgProgram>> {
        if (channels.isEmpty()) return emptyMap()
        val candidateIds = channels.flatMap { listOfNotNull(it.epgChannelId, it.id) }.distinct()
        val rowsByRawId = dao.getProgrammes(candidateIds, from.epochSecond, to.epochSecond)
            .groupBy(keySelector = { it.channelId }, valueTransform = { it.toEpgProgram() })

        return channels.associate { channel ->
            val programmes = channel.epgChannelId?.let { rowsByRawId[it] } ?: rowsByRawId[channel.id].orEmpty()
            channel.epgKey to programmes
        }
    }

    private fun xmltvUrlFor(config: IptvSourceConfig): String? = when (config) {
        is IptvSourceConfig.Xtream -> config.xmlTvUrlOverride?.takeIf(String::isNotBlank) ?: config.baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("xmltv.php")
            ?.addQueryParameter("username", config.username)
            ?.addQueryParameter("password", config.password)
            ?.build()
            ?.toString()
        is IptvSourceConfig.M3u -> config.epgUrl
    }

    private fun describeFetchFailure(throwable: Throwable): String = when (throwable) {
        is HttpStatusException -> "Guide download failed: HTTP ${throwable.code}"
        is SAXException -> "Guide downloaded but couldn't be read as XMLTV (${throwable.message ?: throwable::class.simpleName})"
        is IOException -> "Guide download failed: ${throwable.message ?: "network error"}"
        else -> "Guide download failed: ${throwable.message ?: throwable::class.simpleName}"
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")
    private class EmptyBodyException : IOException("Empty response body")

    /**
     * Streams the response body straight into the SAX parser - never materializes the raw XML as
     * a String/byte buffer first. A ~283MB single-allocation OutOfMemoryError was observed on a
     * real device from the old fetch-whole-body-then-parse approach; some providers' xmltv.php
     * dumps really are that large. keepFrom/keepUntil additionally bound the *parsed* result to
     * roughly the window callers actually use, so a guide spanning months of data doesn't also
     * balloon the in-memory result list or the Room insert.
     */
    private fun fetchAndParse(url: String, onProgress: (Float) -> Unit): Result<List<XmlTvProgramme>> = runCatching {
        val now = Instant.now()
        val keepFrom = now.minus(1, ChronoUnit.DAYS)
        val keepUntil = now.plus(8, ChronoUnit.DAYS)

        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw EmptyBodyException()
            val contentLength = body.contentLength()
            val progressStream = ProgressInputStream(body.byteStream(), contentLength, onProgress)
            XmlTvParser.parse(progressStream, keepFrom = keepFrom, keepUntil = keepUntil)
        }
    }

    /** Reports download progress as SAX consumes the stream, rather than reporting 100% only once the whole body has separately been buffered first. */
    private class ProgressInputStream(
        delegate: InputStream,
        private val totalBytes: Long,
        private val onProgress: (Float) -> Unit,
    ) : FilterInputStream(delegate) {
        private var bytesRead = 0L

        override fun read(): Int {
            val b = super.read()
            if (b != -1) track(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) track(n.toLong())
            return n
        }

        private fun track(n: Long) {
            bytesRead += n
            if (totalBytes > 0) onProgress((bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
        }
    }
}
