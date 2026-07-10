package com.android.streamhub.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import com.android.streamhub.feature.iptv.data.VodMovieInfo
import com.android.streamhub.feature.iptv.data.VodShowInfo
import com.android.streamhub.feature.iptv.livetv.ReminderProgramDialog
import com.android.streamhub.feature.iptv.livetv.RecordProgramDialog
import com.android.streamhub.feature.iptv.livetv.dayTimeFormatter
import com.android.streamhub.feature.iptv.livetv.rememberUse24HourTime
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import java.time.ZoneId

// Shared across phone and TV rather than split (same reasoning as RecordingsScreen) - result rows
// are plain list content, nothing orientation-specific enough to justify two files, and wraps its
// own MaterialTheme since it's reachable from the TV nav host too (tv-material3's ambient theme,
// not this one).

private sealed class ProgramDialogState {
    data class Record(val channel: IptvChannelInfo, val program: EpgProgram) : ProgramDialogState()
    data class Reminder(val channel: IptvChannelInfo, val program: EpgProgram) : ProgramDialogState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    onPlayChannel: (channelId: String) -> Unit,
    onOpenVodMovie: (itemId: String) -> Unit,
    onOpenVodShow: (seriesId: String) -> Unit,
    onOpenJellyfinItem: (JellyfinItemInfo) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialogState by remember { mutableStateOf<ProgramDialogState?>(null) }
    var startAdjustMinutes by remember { mutableIntStateOf(0) }
    var endAdjustMinutes by remember { mutableIntStateOf(0) }
    var leadMinutes by remember { mutableIntStateOf(10) }

    // Voice search only needs the intent to resolve to a handler (e.g. Google app) - no
    // RECORD_AUDIO permission is required here since ACTION_RECOGNIZE_SPEECH hands the actual
    // microphone capture off to that separate app entirely, this activity never touches audio.
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search StreamHub")
    }
    val voiceSearchAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::onQueryChange)
        }
    }

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp, 12.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search live TV, movies, shows…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (uiState.query.isNotEmpty()) {
                            { IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") } }
                        } else null,
                        singleLine = true,
                        shape = AppShapes.pill,
                        colors = OutlinedTextFieldDefaults.colors(),
                        modifier = Modifier.weight(1f),
                    )
                    if (voiceSearchAvailable) {
                        IconButton(onClick = { voiceLauncher.launch(voiceIntent) }, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(Icons.Filled.Mic, contentDescription = "Voice search")
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.query.isBlank() -> EmptyPrompt("Search across Live TV, EPG, VOD, and Jellyfin - all in one place.")
                        uiState.isSearching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        uiState.hasSearched && uiState.isEmpty -> EmptyPrompt("No results for \"${uiState.query}\"")
                        else -> SearchResultsTabs(
                            uiState = uiState,
                            onPlayChannel = onPlayChannel,
                            onOpenVodMovie = onOpenVodMovie,
                            onOpenVodShow = onOpenVodShow,
                            onOpenJellyfinItem = onOpenJellyfinItem,
                            onRecord = { channel, program ->
                                startAdjustMinutes = 0
                                endAdjustMinutes = 0
                                dialogState = ProgramDialogState.Record(channel, program)
                            },
                            onReminder = { channel, program ->
                                leadMinutes = 10
                                dialogState = ProgramDialogState.Reminder(channel, program)
                            },
                        )
                    }
                }
            }
        }
    }

    when (val state = dialogState) {
        is ProgramDialogState.Record -> RecordProgramDialog(
            channel = state.channel,
            program = state.program,
            startAdjustMinutes = startAdjustMinutes,
            endAdjustMinutes = endAdjustMinutes,
            onStartAdjustChange = { startAdjustMinutes = it },
            onEndAdjustChange = { endAdjustMinutes = it },
            onDismiss = { dialogState = null },
            onConfirm = {
                viewModel.scheduleRecording(state.channel, state.program, startAdjustMinutes, endAdjustMinutes)
                dialogState = null
            },
        )
        is ProgramDialogState.Reminder -> ReminderProgramDialog(
            channel = state.channel,
            program = state.program,
            leadMinutes = leadMinutes,
            onLeadMinutesChange = { leadMinutes = it },
            onDismiss = { dialogState = null },
            onConfirm = {
                viewModel.scheduleReminder(state.channel, state.program, leadMinutes)
                dialogState = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun EmptyPrompt(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = message, color = Palette.TextMuted, textAlign = TextAlign.Center)
    }
}

private enum class SearchTab(val label: String) {
    LIVE_TV("Live TV"),
    VOD("VOD"),
    JELLYFIN("Jellyfin"),
    EMBY("Emby"),
}

/**
 * Results grouped into a top TabRow (one tab per source) rather than one long scrolling list -
 * requested explicitly so each source reads as its own focused list instead of having to scroll
 * past every other source to reach the one you actually want.
 */
@Composable
private fun SearchResultsTabs(
    uiState: SearchUiState,
    onPlayChannel: (String) -> Unit,
    onOpenVodMovie: (String) -> Unit,
    onOpenVodShow: (String) -> Unit,
    onOpenJellyfinItem: (JellyfinItemInfo) -> Unit,
    onRecord: (IptvChannelInfo, EpgProgram) -> Unit,
    onReminder: (IptvChannelInfo, EpgProgram) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(SearchTab.LIVE_TV) }
    val use24Hour = rememberUse24HourTime()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            SearchTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    text = { Text(tabLabel(tab, uiState)) },
                )
            }
        }
        when (selectedTab) {
            SearchTab.LIVE_TV -> LiveTvResultsTab(
                results = uiState.epgResults,
                use24Hour = use24Hour,
                onPlayChannel = onPlayChannel,
                onRecord = onRecord,
                onReminder = onReminder,
            )
            SearchTab.VOD -> VodResultsTab(
                movies = uiState.vodMovies,
                shows = uiState.vodShows,
                onOpenMovie = onOpenVodMovie,
                onOpenShow = onOpenVodShow,
            )
            SearchTab.JELLYFIN -> JellyfinResultsTab(results = uiState.jellyfinResults, onOpenItem = onOpenJellyfinItem)
            SearchTab.EMBY -> EmptyPrompt("Emby integration isn't wired up yet.")
        }
    }
}

private fun tabLabel(tab: SearchTab, uiState: SearchUiState): String {
    if (tab == SearchTab.EMBY) return tab.label
    val count = when (tab) {
        SearchTab.LIVE_TV -> uiState.epgResults.size
        SearchTab.VOD -> uiState.vodMovies.size + uiState.vodShows.size
        SearchTab.JELLYFIN -> uiState.jellyfinResults.size
        SearchTab.EMBY -> 0
    }
    return "${tab.label} ($count)"
}

@Composable
private fun LiveTvResultsTab(
    results: List<EpgSearchResult>,
    use24Hour: Boolean,
    onPlayChannel: (String) -> Unit,
    onRecord: (IptvChannelInfo, EpgProgram) -> Unit,
    onReminder: (IptvChannelInfo, EpgProgram) -> Unit,
) {
    if (results.isEmpty()) {
        EmptyPrompt("No upcoming Live TV matches.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(results, key = { "epg:${it.channel.id}:${it.program.startAt}" }) { result ->
            EpgResultRow(
                result = result,
                use24Hour = use24Hour,
                onClick = { onPlayChannel(result.channel.id) },
                onRecord = { onRecord(result.channel, result.program) },
                onReminder = { onReminder(result.channel, result.program) },
            )
        }
    }
}

@Composable
private fun VodResultsTab(
    movies: List<VodMovieInfo>,
    shows: List<VodShowInfo>,
    onOpenMovie: (String) -> Unit,
    onOpenShow: (String) -> Unit,
) {
    if (movies.isEmpty() && shows.isEmpty()) {
        EmptyPrompt("No VOD matches.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (movies.isNotEmpty()) {
            item { SectionHeader("Movies") }
            items(movies, key = { "movie:${it.id}" }) { movie ->
                ResultRow(
                    title = movie.name,
                    subtitle = null,
                    posterUrl = movie.posterUrl,
                    badgeColor = Palette.SourceIptv,
                    onClick = { onOpenMovie(movie.id) },
                )
            }
        }
        if (shows.isNotEmpty()) {
            item { SectionHeader("TV Shows") }
            items(shows, key = { "show:${it.id}" }) { show ->
                ResultRow(
                    title = show.name,
                    subtitle = null,
                    posterUrl = show.posterUrl,
                    badgeColor = Palette.SourceIptv,
                    onClick = { onOpenShow(show.id) },
                )
            }
        }
    }
}

@Composable
private fun JellyfinResultsTab(results: List<JellyfinItemInfo>, onOpenItem: (JellyfinItemInfo) -> Unit) {
    if (results.isEmpty()) {
        EmptyPrompt("No Jellyfin matches.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(results, key = { "jf:${it.id}" }) { item ->
            ResultRow(
                title = item.name,
                subtitle = item.seriesName,
                posterUrl = item.primaryImageUrl,
                badgeColor = Palette.SourceJellyfin,
                onClick = { onOpenItem(item) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Palette.TextPrimary,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EpgResultRow(
    result: EpgSearchResult,
    use24Hour: Boolean,
    onClick: () -> Unit,
    onRecord: () -> Unit,
    onReminder: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Palette.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (result.channel.logoUrl != null) {
                AsyncImage(
                    model = result.channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            } else {
                Text(text = result.channel.name.take(2).uppercase(), color = Palette.TextMuted, fontSize = 12.sp)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = result.program.title, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${result.channel.name} · ${dayTimeFormatter(use24Hour).format(result.program.startAt.atZone(ZoneId.systemDefault()))}",
                color = Palette.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRecord) { Icon(Icons.Filled.Videocam, contentDescription = "Record", tint = Palette.SourceRecording) }
        IconButton(onClick = onReminder) { Icon(Icons.Filled.NotificationsActive, contentDescription = "Set reminder", tint = Palette.Accent) }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String?,
    posterUrl: String?,
    badgeColor: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier.width(48.dp).aspectRatio(2f / 3f).clip(AppShapes.small).background(Palette.Surface),
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .align(Alignment.TopEnd),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(text = subtitle, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
