package com.android.streamhub.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

data class DownloadsUiState(
    val downloads: List<DownloadInfo> = emptyList(),
    val totalBytesUsed: Long = 0L,
    val freeBytesAvailable: Long = 0L,
)

/**
 * Aggregates DownloadTracker (one shared queue across every source) with a storage summary - the
 * quota line is informational only (usableSpace on the actual partition downloads live on), not
 * an enforced cap, since this is a personal-use app rather than a multi-tenant one.
 */
@HiltViewModel
class DownloadsManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadTracker: DownloadTracker,
) : ViewModel() {
    val uiState: StateFlow<DownloadsUiState> = downloadTracker.downloads
        .map { downloads ->
            DownloadsUiState(
                downloads = downloads,
                totalBytesUsed = downloads.sumOf { it.bytesDownloaded },
                freeBytesAvailable = File(context.filesDir, "downloads").usableSpace,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun pause(id: String) = downloadTracker.pauseDownload(id)
    fun resume(id: String) = downloadTracker.resumeDownload(id)
    fun remove(id: String) = downloadTracker.removeDownload(id)
    fun retry(id: String) = downloadTracker.retryDownload(id)
}
