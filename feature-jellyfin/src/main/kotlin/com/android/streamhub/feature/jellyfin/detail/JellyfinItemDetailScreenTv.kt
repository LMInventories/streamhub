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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadState
import com.android.streamhub.core.tmdb.PersonLookupState
import com.android.streamhub.feature.jellyfin.data.JellyfinAudioTrackInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinCastMember
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType
import com.android.streamhub.feature.jellyfin.data.JellyfinSubtitleTrackInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinVersionInfo

/** TV-native sibling of JellyfinItemDetailScreen - same JellyfinItemDetailViewModel, tv-material3 components + TvFocusBorder on the custom (non-Card/Button) rows. */
@Composable
fun JellyfinItemDetailScreenTv(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenPerson: (tmdbPersonId: Int, sourceType: SourceType) -> Unit,
    viewModel: JellyfinItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()
    val personLookupState by viewModel.personLookupState.collectAsStateWithLifecycle()

    LaunchedEffect(personLookupState) {
        val found = personLookupState as? PersonLookupState.Found ?: return@LaunchedEffect
        onOpenPerson(found.tmdbPersonId, SourceType.JELLYFIN)
        viewModel.consumePersonLookup()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            // No title here on purpose - see the phone screen's matching comment. The Spacer keeps
            // the watched/favourite actions pinned right now that nothing else claims the space.
            Spacer(modifier = Modifier.weight(1f))
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

        // weight(1f) is load-bearing here, not decorative - without it, the fillMaxSize() content
        // below would be measured against the whole Column's height (same budget the title Row
        // already consumed from), not what's actually left after it, and would get placed starting
        // below the title Row while still claiming full-screen height - pushing its own bottom edge
        // that same distance past the visible screen, with no way to scroll the hidden portion back
        // into view.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    selectedAudioIndex = uiState.selectedAudioIndex,
                    onSelectAudioTrack = viewModel::selectAudioTrack,
                    selectedVersionId = uiState.selectedVersionId,
                    onSelectVideoVersion = viewModel::selectVideoVersion,
                    onPlay = onPlay,
                    onOpenSeries = onOpenSeries,
                    onOpenEpisode = onOpenEpisode,
                    onPersonClick = viewModel::onPersonClick,
                    onStartDownload = viewModel::startDownload,
                    onPauseDownload = viewModel::pauseDownload,
                    onResumeDownload = viewModel::resumeDownload,
                    onRemoveDownload = viewModel::removeDownload,
                )
            }
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
    selectedAudioIndex: Int?,
    onSelectAudioTrack: (Int) -> Unit,
    selectedVersionId: String?,
    onSelectVideoVersion: (String) -> Unit,
    onPlay: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onPersonClick: (String) -> Unit,
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
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp, 16.dp)) {
                // Episodes get a small 16:9 scene-thumbnail instead of the narrow 2:3 poster - see
                // the phone screen's matching comment for why. Sized to sit beside the title/
                // metadata/buttons rather than as a full-bleed hero above them.
                Box(
                    modifier = (if (isEpisode) Modifier.width(200.dp).aspectRatio(16f / 9f) else Modifier.width(140.dp).height(210.dp))
                        .clip(AppShapes.small),
                ) {
                    val imageUrl = if (isEpisode) item.episodeThumbnailUrl ?: item.primaryImageUrl else item.primaryImageUrl
                    if (imageUrl != null) {
                        AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                    }
                }

                Column(modifier = Modifier.padding(start = 20.dp)) {
                    // Promoted to a bold two-line heading - see JellyfinItemDetailScreen's matching
                    // comment for why a single muted micro-text line wasn't enough here.
                    if (isEpisode && item.seriesName != null) {
                        Text(text = item.seriesName, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = buildString {
                                if (item.parentIndexNumber != null && item.indexNumber != null) {
                                    append("Season ${item.parentIndexNumber} · Episode ${item.indexNumber} - ")
                                }
                                append(item.name)
                            },
                            color = Palette.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    val metaParts = listOfNotNull(
                        // Episodes show their actual air date (when Jellyfin has one) rather than
                        // just the series' production year - see the phone screen's matching logic.
                        if (isEpisode) item.premiereDateLabel ?: item.productionYear?.toString() else item.productionYear?.toString(),
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
                selectedAudioIndex = selectedAudioIndex,
                onSelectAudioTrack = onSelectAudioTrack,
                selectedVersionId = selectedVersionId,
                onSelectVideoVersion = onSelectVideoVersion,
            )
        }

        item.overview?.let { overview ->
            item {
                Text(
                    text = overview,
                    color = Palette.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
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
        if (item.crew.isNotEmpty()) {
            item {
                Text(
                    text = "Director: ${item.crew.joinToString(", ") { it.name }}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }

        if (item.externalLinks.isNotEmpty()) {
            item {
                ExternalLinksRowTv(links = item.externalLinks)
            }
        }

        if (isEpisode && seasonEpisodes.isNotEmpty()) {
            item {
                Text(
                    text = "More from Season ${item.parentIndexNumber ?: ""}".trimEnd(),
                    color = Palette.TextPrimary,
                    modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 12.dp),
                )
            }
            item {
                // Starts scrolled to the next episode (the current one is already excluded from
                // this list - see seasonEpisodes' own doc) rather than always episode 1 - same fix
                // as the phone screen's matching row, see its own comment for the season-finale
                // fallback reasoning.
                val initialEpisodeIndex = remember(item.id, seasonEpisodes) {
                    seasonEpisodes.indexOfFirst { it.indexNumber != null && item.indexNumber != null && it.indexNumber > item.indexNumber }
                        .takeIf { it >= 0 } ?: (seasonEpisodes.size - 1).coerceAtLeast(0)
                }
                LazyRow(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = initialEpisodeIndex),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    // 20dp (was 12dp) - EpisodeThumbnailCardTv grows 15% on focus without
                    // affecting layout size, so this needs slack for that growth.
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(seasonEpisodes, key = { it.id }) { episode ->
                        EpisodeThumbnailCardTv(episode = episode, onClick = { onOpenEpisode(episode.id) })
                    }
                }
            }
        }

        // Same crew/guest-star split as the phone screen - see that composable's matching comment.
        val castAndCrew = if (isEpisode) item.crew else item.cast
        if (castAndCrew.isNotEmpty()) {
            item {
                Text(text = "Cast & Crew", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                TvCastRow(cast = castAndCrew, onPersonClick = onPersonClick)
            }
        }

        if (isEpisode && item.guestStars.isNotEmpty()) {
            item {
                Text(text = "Guest Stars", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                TvCastRow(cast = item.guestStars, onPersonClick = onPersonClick)
            }
        }
    }
}

/** "More from Season X" card - a real 16:9 scene thumbnail (not the series poster JellyfinPosterTv would show for an episode) captioned "N. Name" (matches the official Jellyfin app) with a watched checkmark overlay when already seen. Not private - reused as-is by JellyfinSeriesDetailScreenTv's own per-season episode row (same package). */
@Composable
fun EpisodeThumbnailCardTv(episode: JellyfinItemInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp)) {
        Card(onClick = onClick, scale = CardDefaults.scale(focusedScale = 1.15f), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(AppShapes.small)) {
                val thumbUrl = episode.episodeThumbnailUrl ?: episode.primaryImageUrl
                if (thumbUrl != null) {
                    AsyncImage(model = thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
                if (episode.isPlayed) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Watched",
                        tint = Palette.Accent,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                    )
                }
            }
        }
        Text(
            text = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name.takeIf { it.isNotBlank() }).joinToString(" "),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** TV equivalent of the phone ExternalLinksRow - a "IMDb, Trakt, ..." line of tappable links, shared by both TV detail screens (same package). tvFocusBorder is needed here (unlike Button/Card) since this is a plain clickable Text, same reasoning as every other custom clickable row in this file. */
@Composable
fun ExternalLinksRowTv(links: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth().padding(24.dp, 4.dp)) {
        links.forEachIndexed { index, (name, url) ->
            if (index > 0) {
                Text(text = ", ", color = Palette.TextMuted)
            }
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = name,
                color = Palette.Accent,
                modifier = Modifier
                    .tvFocusBorder(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
            )
        }
    }
}

/** Shared cast row for both TV detail screens - tv-material3 Card equivalent of the phone CastMemberCard. */
@Composable
fun TvCastRow(cast: List<JellyfinCastMember>, onPersonClick: (String) -> Unit = {}) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cast, key = { it.id }) { member ->
            Column(modifier = Modifier.width(100.dp)) {
                Card(onClick = { onPersonClick(member.name) }, modifier = Modifier.size(100.dp)) {
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
    selectedAudioIndex: Int?,
    onSelectAudioTrack: (Int) -> Unit,
    selectedVersionId: String?,
    onSelectVideoVersion: (String) -> Unit,
) {
    if (item.videoLabel == null && item.audioLabel == null && item.subtitleTracks.isEmpty() &&
        item.videoVersions.isEmpty() && item.audioTracks.isEmpty()
    ) {
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp, 8.dp)) {
        when {
            item.videoVersions.size > 1 -> VersionPickerRowTv(item.videoVersions, selectedVersionId, onSelectVideoVersion)
            item.videoLabel != null -> MediaInfoRowTv(label = "Video", value = item.videoLabel)
        }
        when {
            item.audioTracks.size > 1 -> AudioPickerRowTv(item.audioTracks, selectedAudioIndex, onSelectAudioTrack)
            item.audioLabel != null -> MediaInfoRowTv(label = "Audio", value = item.audioLabel)
        }
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
        Text(text = label, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Text(text = value, color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun forcedSuffixTv(track: JellyfinSubtitleTrackInfo): String = if (track.isForced) " (Forced)" else ""

@Composable
private fun SubtitlePickerRowTv(
    tracks: List<JellyfinSubtitleTrackInfo>,
    selectedIndex: Int?,
    explicitlyOff: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = tracks.firstOrNull { it.index == selectedIndex }?.let { it.label + forcedSuffixTv(it) } ?: "Off"

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "Subtitles", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Button(onClick = { expanded = true }) {
            Text(selectedLabel, style = MaterialTheme.typography.bodyMedium)
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
                // Off only hard-disables regular subtitles - a forced track (e.g. the one
                // foreign-language scene in an otherwise-English film) still shows, so this needs
                // calling out here or the behavior looks like a bug. Plain TvDialogRow has no
                // secondary-text slot, so this gets its own muted row rather than changing that
                // shared component (also used by the audio/version pickers) just for this.
                if (tracks.any { it.isForced }) {
                    Text(
                        "Forced subtitles still show when available",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                tracks.forEach { track ->
                    TvDialogRow(
                        label = (track.label + forcedSuffixTv(track)).let { if (track.index == selectedIndex) "$it ✓" else it },
                        onClick = { onSelect(track.index); expanded = false },
                    )
                }
            }
        }
    }
}

/** Same shape as SubtitlePickerRowTv above, minus the "Off" entry - there's no off-concept for audio, a track is always selected. */
@Composable
private fun AudioPickerRowTv(tracks: List<JellyfinAudioTrackInfo>, selectedIndex: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = tracks.firstOrNull { it.index == selectedIndex }?.label ?: tracks.first().label

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "Audio", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Button(onClick = { expanded = true }) {
            Text(selectedLabel, style = MaterialTheme.typography.bodyMedium)
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
                tracks.forEachIndexed { i, track ->
                    TvDialogRow(
                        label = if (track.index == selectedIndex) "${track.label} ✓" else track.label,
                        onClick = { onSelect(track.index); expanded = false },
                        modifier = if (i == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

/** Same shape as AudioPickerRowTv above, keyed by JellyfinVersionInfo.id (a String) instead of a track index. */
@Composable
private fun VersionPickerRowTv(versions: List<JellyfinVersionInfo>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = versions.firstOrNull { it.id == selectedId }?.label ?: versions.first().label

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "Version", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Button(onClick = { expanded = true }) {
            Text(selectedLabel, style = MaterialTheme.typography.bodyMedium)
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
                versions.forEachIndexed { i, version ->
                    TvDialogRow(
                        label = if (version.id == selectedId) "${version.label} ✓" else version.label,
                        onClick = { onSelect(version.id); expanded = false },
                        modifier = if (i == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
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
