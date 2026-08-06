package com.android.streamhub.feature.jellyfin.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.android.streamhub.feature.jellyfin.data.JellyfinAudioTrackInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinCastMember
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinItemType
import com.android.streamhub.feature.jellyfin.data.JellyfinSubtitleTrackInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinVersionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinItemDetailScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: JellyfinItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    // No title here on purpose - the item's name is already the bold heading in the
                    // content below, and repeating it in the app bar's own (differently styled)
                    // typography read as inconsistent rather than helpful.
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
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
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                // weight(1f) is load-bearing here, not decorative - without it, the fillMaxSize()
                // content below would be measured against the whole Column's height (same budget
                // TopAppBar already consumed from), not what's actually left after it, and would
                // get placed starting below TopAppBar while still claiming full-screen height -
                // pushing its own bottom edge that same distance past the visible screen, with no
                // way to scroll the hidden portion back into view.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        uiState.item == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                        }
                        else -> JellyfinItemDetailContent(
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
}

@Composable
private fun JellyfinItemDetailContent(
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
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val context = LocalContext.current
    val isEpisode = item.type == JellyfinItemType.EPISODE
    // Grabs initial D-pad focus on entry so a TV remote's first OK press does the obvious thing
    // (start playback) instead of landing wherever the focus system defaults to - harmless on
    // touch devices, which have no focus ring to show.
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Episodes get a small 16:9 scene-thumbnail instead of the narrow 2:3 poster - a
            // poster-shaped box for an episode either shows the series' own poster (confusing next
            // to an episode-specific page) or a stretched scene-grab, neither of which reads as
            // intentional the way a proper 16:9 thumbnail does. Sized to sit beside the title/
            // metadata/buttons rather than as a full-bleed hero above them.
            Box(
                modifier = (if (isEpisode) Modifier.width(110.dp).aspectRatio(16f / 9f) else Modifier.width(120.dp).height(180.dp))
                    .clip(AppShapes.small),
            ) {
                val imageUrl = if (isEpisode) item.episodeThumbnailUrl ?: item.primaryImageUrl else item.primaryImageUrl
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
                // Promoted to a bold two-line heading (show name, then "Season X · Episode Y -
                // Title") rather than a single muted micro-text line - the show/episode title is
                // the single most important thing on this screen, and TopAppBar's own title is too
                // small/easy to miss to carry that alone.
                if (isEpisode && item.seriesName != null) {
                    Text(text = item.seriesName, color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = buildString {
                            if (item.parentIndexNumber != null && item.indexNumber != null) {
                                append("Season ${item.parentIndexNumber} · Episode ${item.indexNumber} - ")
                            }
                            append(item.name)
                        },
                        color = Palette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val metaParts = listOfNotNull(
                    // Episodes show their actual air date (when Jellyfin has one) rather than just
                    // the series' production year, which is a lot less useful per-episode.
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
                    DownloadButton(
                        downloadInfo = downloadInfo,
                        onStart = onStartDownload,
                        onPause = onPauseDownload,
                        onResume = onResumeDownload,
                        onRemove = onRemoveDownload,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    IconButton(
                        onClick = {
                            // No server-side "trailer" field is used here - a YouTube search is
                            // guaranteed to return something useful regardless of whether this
                            // server/item has a trailer configured at all, unlike relying on
                            // Jellyfin's own (frequently empty) RemoteTrailers data.
                            val query = Uri.encode(listOfNotNull(item.name, item.productionYear?.toString(), "trailer").joinToString(" "))
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(Icons.Filled.Movie, contentDescription = "Search for trailer on YouTube")
                    }
                }
                if (resumeFraction != null) {
                    SignalBar(progress = resumeFraction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), segmentCount = 20)
                }

                item.seriesId?.let { seriesId ->
                    val interactionSource = remember { MutableInteractionSource() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(AppShapes.small)
                            .tvFocusBorder(interactionSource, AppShapes.small)
                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onOpenSeries(seriesId) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = "Go to Show", color = Palette.Accent)
                    }
                }
            }
        }

        MediaInfoSection(
            item = item,
            selectedSubtitleIndex = selectedSubtitleIndex,
            subtitlesExplicitlyOff = subtitlesExplicitlyOff,
            onSelectSubtitle = onSelectSubtitle,
            selectedAudioIndex = selectedAudioIndex,
            onSelectAudioTrack = onSelectAudioTrack,
            selectedVersionId = selectedVersionId,
            onSelectVideoVersion = onSelectVideoVersion,
        )

        item.overview?.let { overview ->
            Text(
                text = overview,
                color = Palette.TextPrimary,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            )
        }

        if (item.genres.isNotEmpty()) {
            Text(
                text = "Genres: ${item.genres.joinToString(", ")}",
                color = Palette.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            )
        }
        if (item.crew.isNotEmpty()) {
            Text(
                text = "Director: ${item.crew.joinToString(", ") { it.name }}",
                color = Palette.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            )
        }

        if (item.externalLinks.isNotEmpty()) {
            ExternalLinksRow(links = item.externalLinks)
        }

        if (isEpisode && seasonEpisodes.isNotEmpty()) {
            Text(
                text = "More from Season ${item.parentIndexNumber ?: ""}".trimEnd(),
                color = Palette.TextPrimary,
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(seasonEpisodes, key = { it.id }) { episode ->
                    EpisodeThumbnailCard(episode = episode, onClick = { onOpenEpisode(episode.id) })
                }
            }
        }

        // Episodes' own people payload doesn't repeat the series' regular cast at all - just its
        // own director(s) (crew) and any episode-specific guest stars, shown as two separate
        // sections. Movies/series have no such split - item.cast (actors) is what "Cast & Crew"
        // shows for them, same as before this section was just labeled "Cast".
        val castAndCrew = if (isEpisode) item.crew else item.cast
        if (castAndCrew.isNotEmpty()) {
            Text(text = "Cast & Crew", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(castAndCrew, key = { it.id }) { member -> CastMemberCard(member) }
            }
        }

        if (isEpisode && item.guestStars.isNotEmpty()) {
            Text(text = "Guest Stars", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(item.guestStars, key = { it.id }) { member -> CastMemberCard(member) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Video/Audio are plain read-only rows when there's only one stream/version - nothing to choose
 * between since this app just plays the source's own default. When an item actually has more than
 * one (a "Version" with multiple encodes, or multiple audio tracks), a picker takes the row's place
 * instead. All three pickers work the same way: picking a value is threaded through
 * JellyfinPlaybackPreferenceStore to the actual player (see that class's doc), so the choice made
 * here is what plays when Play is tapped, not just a local display value.
 */
@Composable
private fun MediaInfoSection(
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

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        when {
            item.videoVersions.size > 1 -> VersionSelectorRow(item.videoVersions, selectedVersionId, onSelectVideoVersion)
            item.videoLabel != null -> MediaInfoRow(label = "Video", value = item.videoLabel)
        }
        when {
            item.audioTracks.size > 1 -> AudioSelectorRow(item.audioTracks, selectedAudioIndex, onSelectAudioTrack)
            item.audioLabel != null -> MediaInfoRow(label = "Audio", value = item.audioLabel)
        }
        if (item.subtitleTracks.isNotEmpty()) {
            SubtitleSelectorRow(
                tracks = item.subtitleTracks,
                selectedIndex = selectedSubtitleIndex,
                explicitlyOff = subtitlesExplicitlyOff,
                onSelect = onSelectSubtitle,
            )
        }
    }
}

@Composable
private fun MediaInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
        Text(text = value, color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SubtitleSelectorRow(
    tracks: List<JellyfinSubtitleTrackInfo>,
    selectedIndex: Int?,
    explicitlyOff: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = tracks.firstOrNull { it.index == selectedIndex }?.label ?: "Off"

    // Same two-part TV D-pad fix as every other DropdownMenu in the app: grab focus into the menu
    // when it opens (a plain Popup never does this on its own), and hand focus back to the row
    // that opened it when it closes (otherwise the disposed, previously-focused item leaves
    // nothing focused and TV's focus system falls back to its own default target - the nav rail -
    // instead of landing back here).
    val anchorFocusRequester = remember { FocusRequester() }
    val closeMenu: () -> Unit = { expanded = false; runCatching { anchorFocusRequester.requestFocus() } }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "Subtitles", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(AppShapes.small)
                    .background(Palette.Surface)
                    .focusRequester(anchorFocusRequester)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = selectedLabel, color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
                val firstItemFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
                DropdownMenuItem(
                    text = { Text(if (explicitlyOff) "Off ✓" else "Off") },
                    modifier = Modifier.focusRequester(firstItemFocusRequester),
                    onClick = { onSelect(null); closeMenu() },
                )
                tracks.forEach { track ->
                    DropdownMenuItem(
                        text = { Text(if (track.index == selectedIndex) "${track.label} ✓" else track.label) },
                        onClick = { onSelect(track.index); closeMenu() },
                    )
                }
            }
        }
    }
}

/** Same shape/styling as SubtitleSelectorRow above, minus the "Off" entry - there's no off-concept for audio, a track is always selected. */
@Composable
private fun AudioSelectorRow(tracks: List<JellyfinAudioTrackInfo>, selectedIndex: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = tracks.firstOrNull { it.index == selectedIndex }?.label ?: tracks.first().label
    val anchorFocusRequester = remember { FocusRequester() }
    val closeMenu: () -> Unit = { expanded = false; runCatching { anchorFocusRequester.requestFocus() } }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "Audio", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(AppShapes.small)
                    .background(Palette.Surface)
                    .focusRequester(anchorFocusRequester)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = selectedLabel, color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
                val firstItemFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
                tracks.forEachIndexed { i, track ->
                    DropdownMenuItem(
                        text = { Text(if (track.index == selectedIndex) "${track.label} ✓" else track.label) },
                        modifier = if (i == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                        onClick = { onSelect(track.index); closeMenu() },
                    )
                }
            }
        }
    }
}

/** Same shape/styling as AudioSelectorRow above, keyed by JellyfinVersionInfo.id (a String) instead of a track index. */
@Composable
private fun VersionSelectorRow(versions: List<JellyfinVersionInfo>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = versions.firstOrNull { it.id == selectedId }?.label ?: versions.first().label
    val anchorFocusRequester = remember { FocusRequester() }
    val closeMenu: () -> Unit = { expanded = false; runCatching { anchorFocusRequester.requestFocus() } }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "Version", color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(AppShapes.small)
                    .background(Palette.Surface)
                    .focusRequester(anchorFocusRequester)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = selectedLabel, color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = closeMenu) {
                val firstItemFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
                versions.forEachIndexed { i, version ->
                    DropdownMenuItem(
                        text = { Text(if (version.id == selectedId) "${version.label} ✓" else version.label) },
                        modifier = if (i == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                        onClick = { onSelect(version.id); closeMenu() },
                    )
                }
            }
        }
    }
}

/**
 * State-driven download control - one tap does whatever's the obvious next action for the
 * current state (start/pause/resume/remove), same "one control, state decides the verb" shape as
 * the Play/Resume button above it. Not shared via :core-player since that module has no material3
 * dependency and this is small enough to duplicate once from feature-iptv's equivalent rather
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

/** "More from Season X" card - a real 16:9 scene thumbnail (not the series poster JellyfinPoster would show for an episode) captioned "N. Name" (matches the official Jellyfin app) with a watched checkmark overlay when already seen. */
@Composable
private fun EpisodeThumbnailCard(episode: JellyfinItemInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(AppShapes.small)) {
            val thumbUrl = episode.episodeThumbnailUrl ?: episode.primaryImageUrl
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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
        Text(
            text = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name.takeIf { it.isNotBlank() }).joinToString(" "),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A single "IMDb, Trakt, ..." line of tappable links - shared by both the episode/movie and series detail screens (same package, no import needed). Each opens its own URL the same way the trailer-search button above already launches a browser Intent. */
@Composable
fun ExternalLinksRow(links: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
        links.forEachIndexed { index, (name, url) ->
            if (index > 0) {
                Text(text = ", ", color = Palette.TextMuted)
            }
            Text(
                text = name,
                color = Palette.Accent,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
            )
        }
    }
}

@Composable
fun CastMemberCard(member: JellyfinCastMember) {
    Column(modifier = Modifier.width(90.dp)) {
        Box(modifier = Modifier.size(90.dp).clip(AppShapes.small)) {
            if (member.imageUrl != null) {
                AsyncImage(
                    model = member.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
            }
        }
        Text(text = member.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
        member.role?.let { role ->
            Text(text = role, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextMuted)
        }
    }
}
