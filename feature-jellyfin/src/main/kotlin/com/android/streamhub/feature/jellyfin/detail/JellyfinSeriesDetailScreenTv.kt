package com.android.streamhub.feature.jellyfin.detail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.home.JellyfinPosterTv

/** TV-native sibling of JellyfinSeriesDetailScreen - same JellyfinSeriesDetailViewModel, tv-material3 components + TvFocusBorder on the custom (non-Card) rows. Reuses TvCastRow from JellyfinItemDetailScreenTv (same package). */
@Composable
fun JellyfinSeriesDetailScreenTv(
    onBack: () -> Unit,
    onOpenEpisode: (itemId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    viewModel: JellyfinSeriesDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = uiState.series?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            uiState.series?.let { series ->
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (series.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (series.isFavorite) "Remove from favourites" else "Add to favourites",
                        tint = if (series.isFavorite) Palette.Accent else Palette.TextPrimary,
                    )
                }
            }
        }

        // weight(1f) is load-bearing here, not decorative - see JellyfinItemDetailScreenTv's
        // matching comment for why a plain fillMaxSize() second child here would overflow past the
        // screen and permanently hide however much of it the title Row's own height covers,
        // unreachable by scrolling.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.series == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                }
                else -> JellyfinSeriesDetailContentTv(
                    series = uiState.series!!,
                    seasons = uiState.seasons,
                    episodesBySeasonNumber = uiState.episodesBySeasonNumber,
                    similarShows = uiState.similarShows,
                    onOpenEpisode = onOpenEpisode,
                    onOpenSeries = onOpenSeries,
                )
            }
        }
    }
}

@Composable
private fun JellyfinSeriesDetailContentTv(
    series: JellyfinItemInfo,
    seasons: List<JellyfinItemInfo>,
    episodesBySeasonNumber: Map<Int, List<JellyfinItemInfo>>,
    similarShows: List<JellyfinItemInfo>,
    onOpenEpisode: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
) {
    val seasonNumbers = episodesBySeasonNumber.keys.sorted()
    var selectedSeason by remember(seasonNumbers) { mutableStateOf<Int?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp, 8.dp)) {
                Box(modifier = Modifier.width(120.dp).height(180.dp).clip(AppShapes.small)) {
                    if (series.primaryImageUrl != null) {
                        AsyncImage(model = series.primaryImageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                    }
                }
                Column(modifier = Modifier.padding(start = 20.dp)) {
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
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }
        if (series.genres.isNotEmpty()) {
            item {
                Text(
                    text = "Genres: ${series.genres.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }
        if (series.studios.isNotEmpty()) {
            item {
                Text(
                    text = "Studios: ${series.studios.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }
        if (series.externalLinks.isNotEmpty()) {
            item {
                ExternalLinksRowTv(links = series.externalLinks)
            }
        }

        if (seasons.size > 1) {
            item {
                Text(text = "Seasons", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "all_seasons") {
                        AllSeasonsTileTv(selected = selectedSeason == null, onClick = { selectedSeason = null })
                    }
                    items(seasons, key = { it.id }) { season ->
                        JellyfinPosterTv(
                            item = season,
                            badge = season.childCount?.let { count -> "$count ep" },
                            unwatchedCount = season.unplayedItemCount,
                            onClick = { selectedSeason = season.indexNumber ?: 0 },
                        )
                    }
                }
            }
        }

        if (series.cast.isNotEmpty()) {
            item {
                Text(text = "Cast & Crew", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                TvCastRow(cast = series.cast)
            }
        }

        // Horizontal thumbnail row (same EpisodeThumbnailCardTv as the episode detail page's own
        // "More from Season X") rather than a vertical text list - the Next Up episode (if any)
        // is simply wherever it naturally falls in its own season's episode order, rather than a
        // separate huge full-width showcase card above this.
        val visibleSeasons = selectedSeason?.let { listOf(it) } ?: seasonNumbers
        visibleSeasons.forEach { season ->
            val episodes = episodesBySeasonNumber[season].orEmpty()
            if (selectedSeason == null) {
                item(key = "season_$season") {
                    Text(text = "Season $season", color = Palette.TextPrimary, modifier = Modifier.fillMaxWidth().padding(24.dp, 12.dp, 24.dp, 4.dp))
                }
            }
            item(key = "season_${season}_episodes") {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeThumbnailCardTv(episode = episode, onClick = { onOpenEpisode(episode.id) })
                    }
                }
            }
        }

        if (similarShows.isNotEmpty()) {
            item {
                Text(text = "More Like This", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(similarShows, key = { it.id }) { show ->
                        JellyfinPosterTv(item = show, onClick = { onOpenSeries(show.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AllSeasonsTileTv(selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(94.dp)
            .aspectRatio(2f / 3f)
            .clip(AppShapes.small)
            .background(if (selected) Palette.Accent.copy(alpha = 0.2f) else Palette.Surface)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "All Seasons", color = Palette.TextPrimary, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
    }
}
