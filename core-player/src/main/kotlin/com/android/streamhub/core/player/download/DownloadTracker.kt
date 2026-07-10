package com.android.streamhub.core.player.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.android.streamhub.core.common.domain.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val PAUSE_STOP_REASON = 1

/**
 * App-facing wrapper around Media3's DownloadManager - every detail/downloads screen goes
 * through this rather than touching Media3's APIs directly, same reasoning PlayerController
 * exists as a wrapper around a raw ExoPlayer instance. Unlike PlayerController this IS a
 * singleton: downloads are a single global queue, not one per screen.
 */
@UnstableApi
@Singleton
class DownloadTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _downloads = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloads: StateFlow<List<DownloadInfo>> = _downloads.asStateFlow()

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) = refresh()
        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh()
    }

    init {
        downloadManager.addListener(listener)
        refresh()
    }

    private fun refresh() {
        val result = mutableListOf<DownloadInfo>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.download.toDownloadInfo()
            }
        }
        _downloads.value = result
    }

    private fun Download.toDownloadInfo(): DownloadInfo {
        val metadata = runCatching { json.decodeFromString<DownloadMetadata>(request.data.decodeToString()) }.getOrNull()
        val sourceType = metadata?.sourceTypeName?.let { name -> runCatching { SourceType.valueOf(name) }.getOrNull() } ?: SourceType.IPTV
        val downloadState = when (state) {
            Download.STATE_QUEUED -> DownloadState.QUEUED
            Download.STATE_DOWNLOADING -> DownloadState.DOWNLOADING
            Download.STATE_STOPPED -> DownloadState.PAUSED
            Download.STATE_COMPLETED -> DownloadState.COMPLETED
            Download.STATE_REMOVING, Download.STATE_RESTARTING -> DownloadState.REMOVING
            else -> DownloadState.FAILED
        }
        return DownloadInfo(
            id = request.id,
            title = metadata?.title ?: request.id,
            posterUrl = metadata?.posterUrl,
            sourceType = sourceType,
            state = downloadState,
            progressPercent = percentDownloaded,
            bytesDownloaded = bytesDownloaded,
            totalBytes = contentLength,
        )
    }

    fun isDownloaded(id: String): Boolean = downloads.value.any { it.id == id && it.state == DownloadState.COMPLETED }

    fun startDownload(id: String, sourceType: SourceType, title: String, posterUrl: String?, streamUri: String) {
        val metadata = DownloadMetadata(title = title, posterUrl = posterUrl, sourceTypeName = sourceType.name)
        val request = DownloadRequest.Builder(id, Uri.parse(streamUri))
            .setData(json.encodeToString(metadata).encodeToByteArray())
            .build()
        DownloadService.sendAddDownload(context, StreamHubDownloadService::class.java, request, false)
    }

    fun pauseDownload(id: String) {
        DownloadService.sendSetStopReason(context, StreamHubDownloadService::class.java, id, PAUSE_STOP_REASON, false)
    }

    fun resumeDownload(id: String) {
        DownloadService.sendSetStopReason(context, StreamHubDownloadService::class.java, id, Download.STOP_REASON_NONE, false)
    }

    fun removeDownload(id: String) {
        DownloadService.sendRemoveDownload(context, StreamHubDownloadService::class.java, id, false)
    }
}
