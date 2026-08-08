package com.android.streamhub.feature.emby.detail

import androidx.compose.foundation.LocalIndication
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
import com.android.streamhub.feature.emby.data.EmbyAudioTrackInfo
import com.android.streamhub.feature.emby.data.EmbyCastMember
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType
import com.android.streamhub.feature.emby.data.EmbySubtitleTrackInfo
import com.android.streamhub.feature.emby.data.EmbyVersionInfo

/**
 * Phone movie/episode detail screen for Emby - now at parity with JellyfinItemDetailScreen's own
 * controls (favorite/watched toggles, download button, audio/subtitle/version pickers), see
 * EmbyItemDetailViewModel's own doc for what backs each. Still leaner than Jellyfin's screen in a
 * few places EmbyItemInfo simply has no equivalent data for - no crew/guest-star split, no
 * external links row, no trailer-search button, no "More from Season" row - none of those were in
 * this pass's scope. onPlay only carries [itemId] back out to the nav host, which wraps it into
 * Route.playerRoute(itemId, SourceType.EMBY) - this module has no reason to know about that route
 * shape itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbyItemDetailScreen(
    itemId: String,
    onPlay: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EmbyItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadInfo by viewModel.downloadInfo.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    // No title here on purpose - the item's name is already the bold heading in
                    // the content below. Mirrors JellyfinItemDetailScreen's own reasoning.
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

                // weight(1f) is load-bearing here, not decorative - without it, the
                // fillMaxSize() content below would be measured against the whole Column's
                // height (same budget TopAppBar already consumed from), not what's actually left
                // after it, and would get placed starting below TopAppBar while still claiming
                // full-screen height - pushing its own bottom edge that same distance past the
                // visible screen, with no way to scroll the hidden portion back into view.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        uiState.item == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                        }
                        else -> EmbyItemDetailContent(
                            item = uiState.item!!,
                            downloadInfo = downloadInfo,
                            selectedSubtitleIndex = uiState.selectedSubtitleIndex,
                            subtitlesExplicitlyOff = uiState.subtitlesExplicitlyOff,
                            onSelectSubtitle = viewModel::selectSubtitle,
                            selectedAudioIndex = uiState.selectedAudioIndex,
                            onSelectAudioTrack = viewModel::selectAudioTrack,
                            selectedVersionId = uiState.selectedVersionId,
                            onSelectVideoVersion = viewModel::selectVideoVersion,
                            onPlay = { onPlay(itemId) },
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
private fun EmbyItemDetailContent(
    item: EmbyItemInfo,
    downloadInfo: DownloadInfo?,
    selectedSubtitleIndex: Int?,
    subtitlesExplicitlyOff: Boolean,
    onSelectSubtitle: (Int?) -> Unit,
    selectedAudioIndex: Int?,
    onSelectAudioTrack: (Int) -> Unit,
    selectedVersionId: String?,
    onSelectVideoVersion: (String) -> Unit,
    onPlay: () -> Unit,
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val isEpisode = item.type == EmbyItemType.EPISODE
    // Grabs initial D-pad focus on entry so a TV remote's first OK press does the obvious thing
    // (start playback) instead of landing wherever the focus system defaults to - harmless on
    // touch devices, which have no focus ring to show. Mirrors JellyfinItemDetailScreen.
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Episodes get a small 16:9 scene-thumbnail instead of the narrow 2:3 poster - a
            // poster-shaped box for an episode would show a stretched scene-grab or nothing at
            // all, neither of which reads as intentional the way a proper 16:9 thumbnail does.
            Box(
                modifier = (if (isEpisode) Modifier.width(110.dp).aspectRatio(16f / 9f) else Modifier.width(120.dp).height(180.dp))
                    .clip(AppShapes.small),
            ) {
                val imageUrl = if (isEpisode) item.episodeThumbnailUrl ?: item.primaryImageUrl else item.primaryImageUrl
                if (imageUrl != null) {
                    AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
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
                } else {
                    Text(text = item.name, color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                val metaParts = listOfNotNull(
                    item.productionYear?.toString(),
                    item.runtimeMinutes?.let { "$it min" },
                    item.communityRating?.let { "★ %.1f".format(it) },
                )
                if (metaParts.isNotEmpty()) {
                    Text(text = metaParts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }

                // Label says "Resume" whenever the server has a played-percentage/resume point
                // recorded for this item, "Play" otherwise - same rule JellyfinItemDetailScreen
                // uses (item.playedPercentage > 0f).
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
                }
                if (resumeFraction != null) {
                    SignalBar(progress = resumeFraction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), segmentCount = 20)
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

        if (item.cast.isNotEmpty()) {
            Text(text = "Cast", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(item.cast, key = { it.id }) { member -> EmbyCastMemberCard(member) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Audio/version are plain pickers only once there's an actual choice - a single-track/
 * single-version item (the overwhelming majority) just shows nothing for that row, since
 * EmbyItemInfo (unlike JellyfinItemInfo) carries no plain read-only videoLabel/audioLabel to fall
 * back to. Subtitle row shows whenever the item has any subtitle tracks at all. All three pickers
 * work the same way: picking a value is threaded through EmbyPlaybackPreferenceStore to the actual
 * player (see that class's doc), so the choice made here is what plays when Play is tapped, not
 * just a local display value. Mirrors JellyfinItemDetailScreen's MediaInfoSection.
 */
@Composable
private fun MediaInfoSection(
    item: EmbyItemInfo,
    selectedSubtitleIndex: Int?,
    subtitlesExplicitlyOff: Boolean,
    onSelectSubtitle: (Int?) -> Unit,
    selectedAudioIndex: Int?,
    onSelectAudioTrack: (Int) -> Unit,
    selectedVersionId: String?,
    onSelectVideoVersion: (String) -> Unit,
) {
    if (item.subtitleTracks.isEmpty() && item.videoVersions.size <= 1 && item.audioTracks.size <= 1) {
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        if (item.videoVersions.size > 1) {
            VersionSelectorRow(item.videoVersions, selectedVersionId, onSelectVideoVersion)
        }
        if (item.audioTracks.size > 1) {
            AudioSelectorRow(item.audioTracks, selectedAudioIndex, onSelectAudioTrack)
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

private fun forcedSuffix(track: EmbySubtitleTrackInfo): String = if (track.isForced) " (Forced)" else ""

@Composable
private fun SubtitleSelectorRow(
    tracks: List<EmbySubtitleTrackInfo>,
    selectedIndex: Int?,
    explicitlyOff: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = tracks.firstOrNull { it.index == selectedIndex }?.let { it.label + forcedSuffix(it) } ?: "Off"

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
                    text = {
                        Column {
                            Text(if (explicitlyOff) "Off ✓" else "Off")
                            // Off only hard-disables regular subtitles - a forced track (e.g. the
                            // one foreign-language scene in an otherwise-English film) still shows,
                            // so this needs calling out here or the behavior looks like a bug.
                            if (tracks.any { it.isForced }) {
                                Text(
                                    "Forced subtitles still show when available",
                                    color = Palette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    modifier = Modifier.focusRequester(firstItemFocusRequester),
                    onClick = { onSelect(null); closeMenu() },
                )
                tracks.forEach { track ->
                    DropdownMenuItem(
                        text = {
                            val label = track.label + forcedSuffix(track)
                            Text(if (track.index == selectedIndex) "$label ✓" else label)
                        },
                        onClick = { onSelect(track.index); closeMenu() },
                    )
                }
            }
        }
    }
}

/** Same shape/styling as SubtitleSelectorRow above, minus the "Off" entry - there's no off-concept for audio, a track is always selected. */
@Composable
private fun AudioSelectorRow(tracks: List<EmbyAudioTrackInfo>, selectedIndex: Int?, onSelect: (Int) -> Unit) {
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

/** Same shape/styling as AudioSelectorRow above, keyed by EmbyVersionInfo.id (a String) instead of a track index. */
@Composable
private fun VersionSelectorRow(versions: List<EmbyVersionInfo>, selectedId: String?, onSelect: (String) -> Unit) {
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
 * the Play/Resume button next to it. Copied verbatim from JellyfinItemDetailScreen's own
 * DownloadButton (not shared - feature modules don't depend on each other).
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

/** Cast card with name/role text only - EmbyCastMember has no image URL field (unlike JellyfinCastMember), so this always shows the same plain placeholder box no AsyncImage branch would ever pick. Public - reused as-is by EmbySeriesDetailScreen (same package). */
@Composable
fun EmbyCastMemberCard(member: EmbyCastMember) {
    Column(modifier = Modifier.width(90.dp)) {
        Box(modifier = Modifier.size(90.dp).clip(AppShapes.small).background(Palette.Surface))
        Text(text = member.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
        member.role?.let { role ->
            Text(text = role, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextMuted)
        }
    }
}
