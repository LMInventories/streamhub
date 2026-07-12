package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadState
import com.android.streamhub.core.ui.phone.theme.appColorScheme

// Reachable from both the phone and TV nav hosts, and built with mobile Material3 components -
// TV's ambient theme is tv-material3's, not this one, so it wraps itself locally rather than
// relying on an ambient theme it can't count on. Same reasoning as EpgGridPanel/IptvSettingsScreen.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(if (uiState.isLoading) "" else uiState.title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                when {
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    uiState.errorMessage != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.errorMessage ?: "", color = Palette.Error, modifier = Modifier.padding(32.dp))
                    }

                    else -> ItemDetailContent(
                        uiState = uiState,
                        downloadInfo = downloadInfo,
                        onPlay = onPlay,
                        onStartDownload = viewModel::startDownload,
                        onPauseDownload = viewModel::pauseDownload,
                        onResumeDownload = viewModel::resumeDownload,
                        onRemoveDownload = viewModel::removeDownload,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDetailContent(
    uiState: ItemDetailUiState,
    downloadInfo: DownloadInfo?,
    onPlay: () -> Unit,
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    // Grabs initial D-pad focus on entry so a TV remote's first OK press does the obvious thing
    // (start playback) instead of landing wherever the focus system defaults to - harmless on
    // touch devices, which have no focus ring to show.
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(AppShapes.small),
            ) {
                if (uiState.posterUrl != null) {
                    AsyncImage(
                        model = uiState.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
                if (uiState.seriesName != null) {
                    Text(
                        text = buildString {
                            append(uiState.seriesName)
                            if (uiState.seasonNumber != null && uiState.episodeNumber != null) {
                                append(" · S${uiState.seasonNumber} E${uiState.episodeNumber}")
                            }
                        },
                        color = Palette.TextMuted,
                    )
                }
                DetailMetaRow(uiState)

                val resumeFraction = uiState.resumeFractionComplete
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    Button(onClick = onPlay, modifier = Modifier.focusRequester(playFocusRequester)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = if (resumeFraction != null) "Resume" else "Play", modifier = Modifier.padding(start = 6.dp))
                    }
                    DownloadButton(
                        downloadInfo = downloadInfo,
                        onStart = onStartDownload,
                        onPause = onPauseDownload,
                        onResume = onResumeDownload,
                        onRemove = onRemoveDownload,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (resumeFraction != null) {
                    SignalBar(progress = resumeFraction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), segmentCount = 20)
                }
            }
        }

        uiState.plot?.let { plot ->
            Text(
                text = plot,
                color = Palette.TextPrimary,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
            uiState.cast?.let { InfoLine(label = "Cast", value = it) }
            uiState.director?.let { InfoLine(label = "Director", value = it) }
            uiState.genre?.let { InfoLine(label = "Genre", value = it) }
            uiState.releaseDate?.let { InfoLine(label = "Released", value = it) }
            uiState.rating?.let { InfoLine(label = "Rating", value = it) }
            uiState.duration?.let { InfoLine(label = "Duration", value = it) }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * State-driven download control - one tap does whatever's the obvious next action for the
 * current state (start/pause/resume/remove), same "one control, state decides the verb" shape as
 * the Play/Resume button above it. Not shared via :core-player since that module has no material3
 * dependency and this is small enough to duplicate once in feature-jellyfin's equivalent rather
 * than adding one just for this.
 */
@Composable
private fun DownloadButton(
    downloadInfo: DownloadInfo?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClick = when (downloadInfo?.state) {
        null, DownloadState.FAILED -> onStart
        DownloadState.PAUSED -> onResume
        DownloadState.QUEUED, DownloadState.DOWNLOADING -> onPause
        DownloadState.COMPLETED -> onRemove
        DownloadState.REMOVING -> ({})
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(AppShapes.small)
            .background(Palette.Surface)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when (downloadInfo?.state) {
            null, DownloadState.FAILED -> {
                Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(18.dp))
                Text("Download", modifier = Modifier.padding(start = 6.dp))
            }
            DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                val progress = downloadInfo.progressPercent
                if (progress >= 0f) {
                    CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.size(18.dp))
                    Text(text = "${progress.toInt()}%", modifier = Modifier.padding(start = 6.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(text = "Downloading", modifier = Modifier.padding(start = 6.dp))
                }
                Icon(Icons.Filled.Pause, contentDescription = "Pause download", modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
            DownloadState.PAUSED -> {
                Icon(Icons.Filled.Download, contentDescription = "Resume download", modifier = Modifier.size(18.dp))
                Text("Paused", modifier = Modifier.padding(start = 6.dp))
            }
            DownloadState.COMPLETED -> {
                Icon(Icons.Filled.Check, contentDescription = "Downloaded", tint = Palette.Accent, modifier = Modifier.size(18.dp))
                Text("Downloaded", modifier = Modifier.padding(start = 6.dp))
                Icon(Icons.Filled.Close, contentDescription = "Remove download", modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
            DownloadState.REMOVING -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Text("Removing", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun DetailMetaRow(uiState: ItemDetailUiState) {
    val parts = listOfNotNull(uiState.genre, uiState.duration, uiState.rating?.let { "★ $it" })
    if (parts.isNotEmpty()) {
        Text(text = parts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = "$label: ", color = Palette.TextMuted)
        Text(text = value, color = Palette.TextPrimary)
    }
}
