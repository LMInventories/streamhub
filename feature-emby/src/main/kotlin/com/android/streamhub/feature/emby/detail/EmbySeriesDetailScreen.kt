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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.emby.data.EmbyItemInfo

/**
 * Phone series detail screen for Emby - hero/overview/cast/season-selector/episode-list, same
 * shape as JellyfinSeriesDetailScreen minus its favorite toggle and "More Like This" row (both
 * out of scope this pass - see EmbySeriesDetailViewModel's own doc). Tapping an episode row plays
 * it directly via [onPlayEpisode] rather than routing through a per-episode detail screen first -
 * a deliberate deviation from JellyfinSeriesDetailScreen (which opens JellyfinItemDetailScreen for
 * the tapped episode) because there's no picker/download/favorite state on this module's item
 * detail screen worth an extra stop before playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbySeriesDetailScreen(
    seriesId: String,
    onPlayEpisode: (episodeId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EmbySeriesDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(uiState.series?.name.orEmpty()) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                // weight(1f) is load-bearing here, not decorative - see EmbyItemDetailScreen's
                // matching comment for why a plain fillMaxSize() second child here would overflow
                // past the screen and permanently hide however much of it TopAppBar's own height
                // covers, unreachable by scrolling.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        uiState.series == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                        }
                        else -> EmbySeriesDetailContent(
                            series = uiState.series!!,
                            seasons = uiState.seasons,
                            episodesBySeasonNumber = uiState.episodesBySeasonNumber,
                            nextUpEpisode = uiState.nextUpEpisode,
                            onPlayEpisode = onPlayEpisode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbySeriesDetailContent(
    series: EmbyItemInfo,
    seasons: List<EmbyItemInfo>,
    episodesBySeasonNumber: Map<Int, List<EmbyItemInfo>>,
    nextUpEpisode: EmbyItemInfo?,
    onPlayEpisode: (String) -> Unit,
) {
    val seasonNumbers = episodesBySeasonNumber.keys.sorted()
    var selectedSeason by remember(seasonNumbers) { mutableStateOf<Int?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Box(modifier = Modifier.width(100.dp).height(150.dp).clip(AppShapes.small)) {
                    if (series.primaryImageUrl != null) {
                        AsyncImage(
                            model = series.primaryImageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    series.communityRating?.let { rating ->
                        Text(text = "★ %.1f".format(rating), color = Palette.TextMuted)
                    }
                    series.overview?.let { overview ->
                        Text(text = overview, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        if (series.genres.isNotEmpty()) {
            item {
                Text(
                    text = "Genres: ${series.genres.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                )
            }
        }

        if (nextUpEpisode != null) {
            item {
                Text(text = "Continue", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            }
            item {
                NextUpCard(episode = nextUpEpisode, onClick = { onPlayEpisode(nextUpEpisode.id) }, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        if (seasons.size > 1) {
            item {
                Text(text = "Seasons", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "all_seasons") {
                        AllSeasonsTile(selected = selectedSeason == null, onClick = { selectedSeason = null })
                    }
                    items(seasons, key = { it.id }) { season ->
                        SeasonTile(season = season, onClick = { selectedSeason = season.indexNumber ?: 0 })
                    }
                }
            }
        }

        if (series.cast.isNotEmpty()) {
            item {
                Text(text = "Cast", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(series.cast, key = { it.id }) { member -> EmbyCastMemberCard(member) }
                }
            }
        }

        val visibleSeasons = selectedSeason?.let { listOf(it) } ?: seasonNumbers
        visibleSeasons.forEach { season ->
            val episodes = episodesBySeasonNumber[season].orEmpty()
            if (selectedSeason == null) {
                item(key = "season_$season") {
                    Text(
                        text = "Season $season",
                        color = Palette.TextPrimary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
                    )
                }
            }
            items(episodes, key = { it.id }) { episode ->
                EpisodeRow(episode = episode, onClick = { onPlayEpisode(episode.id) })
            }
        }
    }
}

/** Wide backdrop-style card for the "Continue" section - tapping plays the episode directly (see EmbySeriesDetailScreen's own doc for why this differs from JellyfinSeriesDetailScreen's "open episode detail first" behavior). */
@Composable
private fun NextUpCard(episode: EmbyItemInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(AppShapes.small)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        val heroUrl = episode.backdropImageUrl ?: episode.episodeThumbnailUrl ?: episode.primaryImageUrl
        if (heroUrl != null) {
            AsyncImage(model = heroUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Palette.TextPrimary,
            modifier = Modifier.align(Alignment.Center).size(40.dp),
        )
        Text(
            text = buildString {
                if (episode.parentIndexNumber != null && episode.indexNumber != null) {
                    append("S${episode.parentIndexNumber}:E${episode.indexNumber} - ")
                }
                append(episode.name)
            },
            color = Palette.TextPrimary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Palette.Surface.copy(alpha = 0.75f))
                .padding(8.dp),
        )
    }
}

@Composable
private fun AllSeasonsTile(selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(2f / 3f)
            .clip(AppShapes.small)
            .background(if (selected) Palette.Accent.copy(alpha = 0.2f) else Palette.Surface)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "All Seasons", color = Palette.TextPrimary, modifier = Modifier.padding(8.dp))
    }
}

/** Poster-shaped season tile - badge shows the season's episode count (EmbyItemInfo.childCount). No unwatched-count badge the way JellyfinPoster's season usage has (unplayedItemCount) - EmbyItemInfo carries no watched-state field at all this pass. */
@Composable
private fun SeasonTile(season: EmbyItemInfo, onClick: () -> Unit) {
    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(modifier = Modifier.aspectRatio(2f / 3f).fillMaxWidth().clip(AppShapes.small)) {
            if (season.primaryImageUrl != null) {
                AsyncImage(model = season.primaryImageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
            }
            season.childCount?.let { count ->
                Text(
                    text = "$count ep",
                    color = Palette.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(AppShapes.small)
                        .background(Palette.Surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(text = season.name, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Episode row - tapping plays the episode directly (trailing PlayArrow hints this rather than opening a detail screen first). */
@Composable
private fun EpisodeRow(episode: EmbyItemInfo, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(16.dp, 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(text = "${episode.indexNumber?.toString() ?: "?"}.", color = Palette.TextMuted, modifier = Modifier.width(28.dp))
                Text(text = episode.name, color = Palette.TextPrimary)
            }
            episode.overview?.let { overview ->
                Text(
                    text = overview,
                    color = Palette.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                )
            }
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.padding(start = 8.dp).size(20.dp))
    }
}
