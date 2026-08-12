package com.android.streamhub.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadState
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTopBar
import com.android.streamhub.core.ui.tv.scaffold.rememberTvSettingsInitialFocus
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.Text as TvText
import kotlin.math.ln
import kotlin.math.pow

// Shared across phone and TV rather than split, same reasoning as RecordingsScreen/
// ScheduledManagementScreen - a plain list with per-row actions doesn't need orientation-specific
// structure, and wraps its own MaterialTheme since it's reachable from the TV nav host too.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsManagementScreen(
    onBack: () -> Unit,
    onOpenDownload: (itemId: String, sourceType: SourceType) -> Unit,
    viewModel: DownloadsManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Downloads") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                if (uiState.downloads.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No downloads yet", color = Palette.TextPrimary)
                            Text(
                                text = "Download a movie or episode from its detail screen to watch it offline.",
                                color = Palette.TextMuted,
                                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${formatBytes(uiState.totalBytesUsed)} used · ${formatBytes(uiState.freeBytesAvailable)} free",
                        color = Palette.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp, 8.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(uiState.downloads, key = { it.id }) { download ->
                            DownloadRow(
                                download = download,
                                onPause = { viewModel.pause(download.id) },
                                onResume = { viewModel.resume(download.id) },
                                onRemove = { viewModel.remove(download.id) },
                                onRetry = { viewModel.retry(download.id) },
                                onOpen = { onOpenDownload(download.id, download.sourceType) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    val isPlayable = download.state == DownloadState.COMPLETED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isPlayable, onClick = onOpen)
            .padding(vertical = 8.dp),
    ) {
        Box(modifier = Modifier.width(48.dp).aspectRatio(2f / 3f).clip(AppShapes.small).background(Palette.Surface)) {
            if (download.posterUrl != null) {
                AsyncImage(
                    model = download.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(sourceBadgeColor(download.sourceType))
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = download.title, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = statusLabel(download),
                color = if (download.state == DownloadState.FAILED) Palette.Error else Palette.TextMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (download.state) {
            DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                if (download.progressPercent >= 0f) {
                    CircularProgressIndicator(progress = { download.progressPercent / 100f }, modifier = Modifier.padding(end = 4.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp))
                }
                IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, contentDescription = "Pause") }
            }
            DownloadState.PAUSED -> IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, contentDescription = "Resume") }
            DownloadState.REMOVING -> CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            DownloadState.FAILED -> IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, contentDescription = "Retry") }
            DownloadState.COMPLETED -> Unit
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Delete", tint = Palette.Error) }
    }
}

private fun statusLabel(download: DownloadInfo): String = when (download.state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.DOWNLOADING -> if (download.progressPercent >= 0f) "${download.progressPercent.toInt()}% · ${formatBytes(download.bytesDownloaded)}" else "Downloading"
    DownloadState.PAUSED -> "Paused · ${formatBytes(download.bytesDownloaded)}"
    DownloadState.COMPLETED -> "Downloaded · ${formatBytes(download.bytesDownloaded)}"
    DownloadState.FAILED -> download.errorMessage?.let { "Failed: $it" } ?: "Failed"
    DownloadState.REMOVING -> "Removing…"
}

private fun sourceBadgeColor(sourceType: SourceType) = when (sourceType) {
    SourceType.JELLYFIN -> Palette.SourceJellyfin
    SourceType.EMBY -> Palette.SourceEmby
    else -> Palette.SourceIptv
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
    return "%.1f %s".format(bytes / 1024.0.pow(digitGroups), units[digitGroups])
}

/** TV-native sibling of DownloadsManagementScreen - same DownloadsManagementViewModel and status/format helpers above. */
@Composable
fun DownloadsManagementScreenTv(
    onBack: () -> Unit,
    onOpenDownload: (itemId: String, sourceType: SourceType) -> Unit,
    viewModel: DownloadsManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // readyKey = isEmpty so this re-fires once the list actually has something to focus - a plain
    // one-shot Unit key would fire before the list exists (or stay stuck focusing nothing if the
    // list was empty on first entry and later gains a download) and silently no-op.
    val firstRowFocusRequester = rememberTvSettingsInitialFocus(readyKey = uiState.downloads.isEmpty())

    Column(modifier = Modifier.fillMaxSize()) {
        TvSettingsTopBar(title = "Downloads", onBack = onBack)

        if (uiState.downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TvText("No downloads yet", color = Palette.TextPrimary)
                    TvText(
                        text = "Download a movie or episode from its detail screen to watch it offline.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                    )
                }
            }
        } else {
            TvText(
                text = "${formatBytes(uiState.totalBytesUsed)} used · ${formatBytes(uiState.freeBytesAvailable)} free",
                color = Palette.TextMuted,
                modifier = Modifier.padding(32.dp, 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
                itemsIndexed(uiState.downloads, key = { _, download -> download.id }) { index, download ->
                    TvDownloadRow(
                        download = download,
                        modifier = if (index == 0) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                        onPause = { viewModel.pause(download.id) },
                        onResume = { viewModel.resume(download.id) },
                        onRemove = { viewModel.remove(download.id) },
                        onRetry = { viewModel.retry(download.id) },
                        onOpen = { onOpenDownload(download.id, download.sourceType) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDownloadRow(
    download: DownloadInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlayable = download.state == DownloadState.COMPLETED
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, enabled = isPlayable, onClick = onOpen)
            .padding(vertical = 10.dp),
    ) {
        Box(modifier = Modifier.width(56.dp).aspectRatio(2f / 3f).clip(AppShapes.small).background(Palette.Surface)) {
            if (download.posterUrl != null) {
                AsyncImage(
                    model = download.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(sourceBadgeColor(download.sourceType))
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            TvText(text = download.title, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TvText(
                text = statusLabel(download),
                color = if (download.state == DownloadState.FAILED) Palette.Error else Palette.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (download.state) {
            DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                if (download.progressPercent >= 0f) {
                    CircularProgressIndicator(progress = { download.progressPercent / 100f }, modifier = Modifier.padding(end = 4.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp))
                }
                TvIconButton(onClick = onPause) { TvIcon(Icons.Filled.Pause, contentDescription = "Pause") }
            }
            DownloadState.PAUSED -> TvIconButton(onClick = onResume) { TvIcon(Icons.Filled.PlayArrow, contentDescription = "Resume") }
            DownloadState.REMOVING -> CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            DownloadState.FAILED -> TvIconButton(onClick = onRetry) { TvIcon(Icons.Filled.Refresh, contentDescription = "Retry") }
            DownloadState.COMPLETED -> Unit
        }
        TvIconButton(onClick = onRemove) { TvIcon(Icons.Filled.Close, contentDescription = "Delete", tint = Palette.Error) }
    }
}
