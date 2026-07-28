package com.android.streamhub.feature.jellyfin.detail

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.home.JellyfinPoster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinSeriesDetailScreen(
    onBack: () -> Unit,
    onOpenEpisode: (itemId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    viewModel: JellyfinSeriesDetailViewModel = hiltViewModel(),
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
                    actions = {
                        uiState.series?.let { series ->
                            IconButton(onClick = viewModel::toggleFavorite) {
                                Icon(
                                    if (series.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (series.isFavorite) "Remove from favourites" else "Add to favourites",
                                    tint = if (series.isFavorite) Palette.Accent else Palette.TextPrimary,
                                )
                            }
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                // weight(1f) is load-bearing here, not decorative - see JellyfinItemDetailScreen's
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
                        else -> JellyfinSeriesDetailContent(
                            series = uiState.series!!,
                            seasons = uiState.seasons,
                            episodesBySeasonNumber = uiState.episodesBySeasonNumber,
                            similarShows = uiState.similarShows,
                            nextUpEpisode = uiState.nextUpEpisode,
                            onOpenEpisode = onOpenEpisode,
                            onOpenSeries = onOpenSeries,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyfinSeriesDetailContent(
    series: JellyfinItemInfo,
    seasons: List<JellyfinItemInfo>,
    episodesBySeasonNumber: Map<Int, List<JellyfinItemInfo>>,
    similarShows: List<JellyfinItemInfo>,
    nextUpEpisode: JellyfinItemInfo?,
    onOpenEpisode: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
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

        if (series.tags.isNotEmpty()) {
            item {
                Text(
                    text = "Tags: ${series.tags.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                )
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
        if (series.studios.isNotEmpty()) {
            item {
                Text(
                    text = "Studios: ${series.studios.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                )
            }
        }
        if (series.externalLinks.isNotEmpty()) {
            item {
                ExternalLinksRow(links = series.externalLinks)
            }
        }

        if (nextUpEpisode != null) {
            item {
                Text(text = "Next Up", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            }
            item {
                NextUpCard(episode = nextUpEpisode, onClick = { onOpenEpisode(nextUpEpisode.id) }, modifier = Modifier.padding(horizontal = 16.dp))
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
                        JellyfinPoster(
                            item = season,
                            badge = season.childCount?.let { count -> "$count ep" },
                            showWatchedBadge = season.isPlayed,
                            onClick = { selectedSeason = season.indexNumber ?: 0 },
                        )
                    }
                }
            }
        }

        if (series.cast.isNotEmpty()) {
            item {
                Text(text = "Cast & Crew", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(series.cast, key = { it.id }) { member -> CastMemberCard(member) }
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
                EpisodeRow(episode = episode, onClick = { onOpenEpisode(episode.id) })
            }
        }

        if (similarShows.isNotEmpty()) {
            item {
                Text(text = "More Like This", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(similarShows, key = { it.id }) { show ->
                        JellyfinPoster(item = show, onClick = { onOpenSeries(show.id) })
                    }
                }
            }
        }
    }
}

/** Wide backdrop-style card (not poster-shaped) for the "Next Up" section - tapping opens that episode's own detail screen, same as every other item row in this app, rather than jumping straight into playback. */
@Composable
private fun NextUpCard(episode: JellyfinItemInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

@Composable
private fun EpisodeRow(episode: JellyfinItemInfo, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(16.dp, 10.dp),
    ) {
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
}
