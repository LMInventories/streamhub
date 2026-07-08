package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.iptv.data.VodEpisodeInfo

// Reachable from both nav hosts - same local-MaterialTheme reasoning as ItemDetailScreen.
private val SeriesDetailColorScheme = darkColorScheme(
    primary = Palette.Accent,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceElevated,
    onSurfaceVariant = Palette.TextMuted,
    outline = Palette.Border,
    error = Palette.Error,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenEpisode: (itemId: String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = SeriesDetailColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(if (uiState.isLoading) "" else uiState.name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                when {
                    uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    uiState.errorMessage != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.errorMessage ?: "", color = Palette.Error, modifier = Modifier.padding(32.dp))
                    }

                    else -> SeriesDetailContent(uiState = uiState, onOpenEpisode = onOpenEpisode)
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailContent(uiState: SeriesDetailUiState, onOpenEpisode: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Box(
                    modifier = Modifier.width(100.dp).height(150.dp).clip(AppShapes.small),
                ) {
                    if (uiState.posterUrl != null) {
                        AsyncImage(
                            model = uiState.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    listOfNotNull(uiState.genre, uiState.rating?.let { "★ $it" }).takeIf { it.isNotEmpty() }?.let { parts ->
                        Text(text = parts.joinToString(" · "), color = Palette.TextMuted)
                    }
                    uiState.plot?.let { plot ->
                        Text(text = plot, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                    }
                    uiState.cast?.let { cast ->
                        Text(text = "Cast: $cast", color = Palette.TextMuted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        uiState.episodesBySeason.toSortedMap().forEach { (season, episodes) ->
            item(key = "season_$season") {
                Text(
                    text = "Season $season",
                    color = Palette.TextPrimary,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
                )
            }
            items(episodes, key = { it.playbackId }) { episode ->
                EpisodeRow(episode = episode, onClick = { onOpenEpisode(episode.playbackId) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: VodEpisodeInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp, 10.dp),
    ) {
        Text(text = "${episode.episodeNumber}.", color = Palette.TextMuted, modifier = Modifier.width(28.dp))
        Text(text = episode.title, color = Palette.TextPrimary)
    }
}
