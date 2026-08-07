package com.android.streamhub.feature.emby.detail

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import com.android.streamhub.feature.emby.data.EmbyItemInfo

/** TV-native sibling of EmbySeriesDetailScreen - same EmbySeriesDetailViewModel, tv-material3 components + tvFocusBorder on the custom (non-Card) rows. Reuses EmbyCastRowTv from EmbyItemDetailScreenTv (same package). */
@Composable
fun EmbySeriesDetailScreenTv(
    seriesId: String,
    onPlayEpisode: (episodeId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EmbySeriesDetailViewModel = hiltViewModel(),
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
        }

        // weight(1f) is load-bearing here, not decorative - see EmbyItemDetailScreenTv's matching
        // comment for why a plain fillMaxSize() second child here would overflow past the screen
        // and permanently hide however much of it the title Row's own height covers, unreachable
        // by scrolling.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.series == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                }
                else -> EmbySeriesDetailContentTv(
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

@Composable
private fun EmbySeriesDetailContentTv(
    series: EmbyItemInfo,
    seasons: List<EmbyItemInfo>,
    episodesBySeasonNumber: Map<Int, List<EmbyItemInfo>>,
    nextUpEpisode: EmbyItemInfo?,
    onPlayEpisode: (String) -> Unit,
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

        if (series.genres.isNotEmpty()) {
            item {
                Text(
                    text = "Genres: ${series.genres.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }

        if (nextUpEpisode != null) {
            item {
                Text(text = "Continue", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                NextUpCardTv(episode = nextUpEpisode, onClick = { onPlayEpisode(nextUpEpisode.id) }, modifier = Modifier.padding(horizontal = 24.dp))
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
                        SeasonTileTv(season = season, onClick = { selectedSeason = season.indexNumber ?: 0 })
                    }
                }
            }
        }

        if (series.cast.isNotEmpty()) {
            item {
                Text(text = "Cast", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                EmbyCastRowTv(cast = series.cast)
            }
        }

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
                        EpisodeThumbnailCardTv(episode = episode, onClick = { onPlayEpisode(episode.id) })
                    }
                }
            }
        }
    }
}

/** TV equivalent of the phone NextUpCard for the "Continue" section - see EmbySeriesDetailScreen's own doc for why tapping plays directly rather than opening episode detail first. Not shown by JellyfinSeriesDetailScreenTv (its own nextUpEpisode state goes unused on TV) but included here since the approved plan calls for a Next Up "Continue" affordance on both form factors. */
@Composable
private fun NextUpCardTv(episode: EmbyItemInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(21f / 9f)
            .clip(AppShapes.small)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
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
            modifier = Modifier.align(Alignment.Center).size(48.dp),
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
                .padding(12.dp),
        )
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

/** TV equivalent of the phone SeasonTile - badge shows episode count only, see that composable's matching comment for why there's no unwatched-count badge. */
@Composable
private fun SeasonTileTv(season: EmbyItemInfo, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(94.dp)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
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
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            text = season.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "Season episode" card - real 16:9 scene thumbnail captioned "N. Name", tapping plays the episode directly (see EmbySeriesDetailScreen's own doc for why this differs from JellyfinSeriesDetailScreenTv's "open episode detail first" behavior). No watched checkmark overlay - EmbyItemInfo carries no watched-state field this pass. */
@Composable
private fun EpisodeThumbnailCardTv(episode: EmbyItemInfo, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = Modifier.width(160.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(AppShapes.small)
                .tvFocusBorder(interactionSource, AppShapes.small)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        ) {
            val thumbUrl = episode.episodeThumbnailUrl ?: episode.primaryImageUrl
            if (thumbUrl != null) {
                AsyncImage(model = thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
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
