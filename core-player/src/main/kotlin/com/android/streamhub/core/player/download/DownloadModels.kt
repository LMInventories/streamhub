package com.android.streamhub.core.player.download

import com.android.streamhub.core.common.domain.SourceType
import kotlinx.serialization.Serializable

enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, REMOVING }

data class DownloadInfo(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val sourceType: SourceType,
    val state: DownloadState,
    // 0..100, -1 if Media3 hasn't determined content length yet (e.g. still resolving an HLS
    // manifest) - callers should treat negative as "show an indeterminate spinner instead".
    val progressPercent: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
)

/**
 * The only per-download metadata this app actually needs beyond what Media3's own DownloadIndex
 * already tracks (state, progress, bytes, uri) - serialized into DownloadRequest.data rather than
 * kept in a parallel Room table, so there's exactly one source of truth for "what's downloaded"
 * instead of two that could drift out of sync. sourceTypeName (not a raw SourceType) so this
 * module doesn't need to add kotlinx.serialization to core-common just for one enum.
 */
@Serializable
data class DownloadMetadata(
    val title: String,
    val posterUrl: String?,
    val sourceTypeName: String,
)
