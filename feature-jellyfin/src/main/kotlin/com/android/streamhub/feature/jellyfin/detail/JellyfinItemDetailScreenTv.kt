package com.android.streamhub.feature.jellyfin.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadState
import com.android.streamhub.feature.jellyfin.data.JellyfinCastMember
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType
import com.android.streamhub.feature.jellyfin.data.JellyfinSubtitleTrackInfo
import com.android.streamhub.feature.jellyfin.home.JellyfinPosterTv

/** TV-native sibling of JellyfinItemDetailScreen - same JellyfinItemDetailViewModel, tv-material3 components + TvFocusBorder on the custom (non-Card/Button) rows. */
@Composable
fun JellyfinItemDetailScreenTv(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: JellyfinItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = uiState.item?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            uiState.item?.let { item ->
                IconButton(onClick = viewModel::toggleWatched) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = if (item.isPlayed) "Mark unwatched" else "Mark watched",
                        tint = if (item.isPlayed) Palette.Accent else Palette.TextMuted,
                    )
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (item.isFavorite) "Remove from favourites" else "Add to favourites",
                        tint = if (item.isFavorite) Palette.Accent else Palette.TextPrimary,
                    )
                }
            }
        }

        when {
            uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.item == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
            }
            else -> JellyfinItemDetailContentTv(
                item = uiState.item!!,
                seasonEpisodes = uiState.seasonEpisodes,
                downloadInfo = downloadInfo,
                selectedSubtitleIndex = uiState.selectedSubtitleIndex,
                subtitlesExplicitlyOff = uiState.subtitlesExplicitlyOff,
                onSelectSubtitle = viewModel::selectSubtitle,
                onPlay = onPlay,
                onOpenSeries = onOpenSeries,
                onOpenEpisode = onOpenEpisode,
                onStartDownload = viewModel::startDownload,
                onPauseDownload = viewModel::pauseDownload,
                onResumeDownload = viewModel::resumeDownload,
                onRemoveDownload = viewModel::removeDownload,
            )
        }
    }
}

@Composable
private fun JellyfinItemDetailContentTv(
    item: JellyfinItemInfo,
    seasonEpisodes: List<JellyfinItemInfo>,
    downloadInfo: DownloadInfo?,
    selectedSubtitleIndex: Int?,
    subtitlesExplicitlyOff: Boolean,
    onSelectSubtitle: (Int?) -> Unit,
    onPlay: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val context = LocalContext.current
    val isEpisode = item.type == JellyfinItemType.EPISODE
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (isEpisode) {
            item {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    val heroUrl = item.episodeThumbnailUrl ?: item.primaryImageUrl
                    if (heroUrl != null) {
                        AsyncImage(model = heroUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp, 16.dp)) {
                if (!isEpisode) {
                    Box(modifier = Modifier.width(140.dp).height(210.dp).clip(AppShapes.small)) {
                        if (item.primaryImageUrl != null) {
                            AsyncImage(model = item.primaryImageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                        }
                    }
                }

                Column(modifier = if (isEpisode) Modifier else Modifier.padding(start = 20.dp)) {
                    if (item.seriesName != null) {
                        Text(
                            text = buildString {
                                append(item.seriesName)
                                if (item.parentIndexNumber != null && item.indexNumber != null) {
                                    append(" · S${item.parentIndexNumber} E${item.indexNumber}")
                                }
                            },
                            color = Palette.TextMuted,
                        )
                    }
                    val metaParts = listOfNotNull(
                        item.productionYear?.toString(),
                        item.runtimeMinutes?.let { "$it min" },
                        item.communityRating?.let { "★ %.1f".format(it) },
                    )
                    if (metaParts.isNotEmpty()) {
                        Text(text = metaParts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                    }

                    val resumeFraction = item.playedPercentage?.takeIf { it > 0f }?.div(100f)
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        Button(onClick = onPlay, modifier = Modifier.focusRequester(playFocusRequester)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(text = if (resumeFraction != null) "Resume" else "Play", modifier = Modifier.padding(start = 6.dp))
                        }
                        DownloadButtonTv(
                            downloadInfo = downloadInfo,
                            onStart = onStartDownload,
                            onPause = onPauseDownload,
                            onResume = onResumeDownload,
                            onRemove = onRemoveDownload,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        Button(
                            onClick = {
                                val query = Uri.encode(listOfNotNull(item.name, item.productionYear?.toString(), "trailer").joinToString(" "))
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
                            },
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Icon(Icons.Filled.Movie, contentDescription = "Search for trailer on YouTube")
                        }
                    }
                    if (resumeFraction != null) {
                        SignalBar(progress = resumeFraction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), segmentCount = 20)
                    }

                    item.seriesId?.let { seriesId ->
                        Button(onClick = { onOpenSeries(seriesId) }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Go to Show")
                        }
                    }
                }
            }
        }

        item {
            MediaInfoSectionTv(
                item = item,
                selectedSubtitleIndex = selectedSubtitleIndex,
                subtitlesExplicitlyOff = subtitlesExplicitlyOff,
                onSelectSubtitle = onSelectSubtitle,
            )
        }

        item.overview?.let { overview ->
            item {
                Text(text = overview, color = Palette.TextPrimary, modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp))
            }
        }

        if (item.genres.isNotEmpty()) {
            item {
                Text(
                    text = "Genres: ${item.genres.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }

        if (isEpisode && seasonEpisodes.isNotEmpty()) {
            item {
                Text(
                    text = "More from Season ${item.parentIndexNumber ?: ""}".trimEnd(),
                    color = Palette.TextPrimary,
                    modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(seasonEpisodes, key = { it.id }) { episode ->
                        JellyfinPosterTv(item = episode, onClick = { onOpenEpisode(episode.id) })
                    }
                }
            }
        }

        if (item.cast.isNotEmpty()) {
            item {
                Text(text = "Cast", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                TvCastRow(cast = item.cast)
            }
        }
    }
}

/** Shared cast row for both TV detail screens - tv-material3 Card equivalent of the phone CastMemberCard. */
@Composable
fun TvCastRow(cast: List<JellyfinCastMember>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cast, key = { it.id }) { member ->
            Column(modifier = Modifier.width(100.dp)) {
                Card(onClick = {}, modifier = Modifier.size(100.dp)) {
                    Box(modifier = Modifier.fillMaxSize().clip(AppShapes.small)) {
                        if (member.imageUrl != null) {
                            AsyncImage(model = member.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                        }
                    }
                }
                Text(text = member.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
                member.role?.let { role ->
                    Text(text = role, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSectionTv(
    item: JellyfinItemInfo,
    selectedSubtitleIndex: Int?,
    subtitlesExplicitlyOff: Boolean,
    onSelectSubtitle: (Int?) -> Unit,
) {
    if (item.videoLabel == null && item.audioLabel == null && item.subtitleTracks.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp, 8.dp)) {
        item.videoLabel?.let { label -> MediaInfoRowTv(label = "Video", value = label) }
        item.audioLabel?.let { label -> MediaInfoRowTv(label = "Audio", value = label) }
        if (item.subtitleTracks.isNotEmpty()) {
            SubtitlePickerRowTv(
                tracks = item.subtitleTracks,
                selectedIndex = selectedSubtitleIndex,
                explicitlyOff = subtitlesExplicitlyOff,
                onSelect = onSelectSubtitle,
            )
        }
    }
}

@Composable
private fun MediaInfoRowTv(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, color = Palette.TextMuted, modifier = Modifier.width(100.dp))
        Text(text = value, color = Palette.TextPrimary)
    }
}

@Composable
private fun SubtitlePickerRowTv(
    tracks: List<JellyfinSubtitleTrackInfo>,
    selectedIndex: Int?,
    explicitlyOff: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = tracks.firstOrNull { it.index == selectedIndex }?.label ?: "Off"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "Subtitles", color = Palette.TextMuted, modifier = Modifier.width(100.dp))
        Button(onClick = { expanded = true }) {
            Text(selectedLabel)
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapes.large)
                    .background(Palette.Surface)
                    .padding(vertical = 8.dp),
            ) {
                val firstItemFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
                TvDialogRow(
                    label = if (explicitlyOff) "Off ✓" else "Off",
                    onClick = { onSelect(null); expanded = false },
                    modifier = Modifier.focusRequester(firstItemFocusRequester),
                )
                tracks.forEach { track ->
                    TvDialogRow(
                        label = if (track.index == selectedIndex) "${track.label} ✓" else track.label,
                        onClick = { onSelect(track.index); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDialogRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(text = label, color = Palette.TextPrimary)
    }
}

/** TV equivalent of the phone DownloadButton - a Button whose label/icon reflects current DownloadState, same "one control, state decides the verb" shape. */
@Composable
private fun DownloadButtonTv(
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
    Button(onClick = onClick, modifier = modifier) {
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
