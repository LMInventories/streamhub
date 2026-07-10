package com.android.streamhub.feature.jellyfin.detail

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinSeriesDetailScreen(
    onBack: () -> Unit,
    onOpenEpisode: (itemId: String) -> Unit,
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

                when {
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    uiState.series == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                    }
                    else -> JellyfinSeriesDetailContent(
                        series = uiState.series!!,
                        episodesBySeasonNumber = uiState.episodesBySeasonNumber,
                        onOpenEpisode = onOpenEpisode,
                    )
                }
            }
        }
    }
}

@Composable
private fun JellyfinSeriesDetailContent(
    series: JellyfinItemInfo,
    episodesBySeasonNumber: Map<Int, List<JellyfinItemInfo>>,
    onOpenEpisode: (String) -> Unit,
) {
    val seasons = episodesBySeasonNumber.keys.sorted()
    var selectedSeason by remember(seasons) { mutableStateOf<Int?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    val metaParts = listOfNotNull(
                        series.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        series.communityRating?.let { "★ %.1f".format(it) },
                    )
                    if (metaParts.isNotEmpty()) {
                        Text(text = metaParts.joinToString(" · "), color = Palette.TextMuted)
                    }
                    series.overview?.let { overview ->
                        Text(text = overview, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        if (seasons.size > 1) {
            item {
                SeasonDropdown(
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    onSelect = { selectedSeason = it },
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                )
            }
        }

        val visibleSeasons = selectedSeason?.let { listOf(it) } ?: seasons
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
    }
}

@Composable
private fun SeasonDropdown(
    seasons: List<Int>,
    selectedSeason: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .tvFocusBorder(interactionSource, AppShapes.small)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = selectedSeason?.let { "Season $it" } ?: "All Seasons", color = Palette.TextPrimary)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Filter by season", tint = Palette.TextPrimary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // A DropdownMenu never moves D-pad focus into itself on TV - without this, the
            // remote's presses kept hitting whatever was focused underneath, not this menu.
            val firstItemFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstItemFocusRequester.requestFocus() }
            DropdownMenuItem(
                text = { Text("All Seasons") },
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                onClick = { onSelect(null); expanded = false },
            )
            seasons.forEach { season ->
                DropdownMenuItem(text = { Text("Season $season") }, onClick = { onSelect(season); expanded = false })
            }
        }
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
