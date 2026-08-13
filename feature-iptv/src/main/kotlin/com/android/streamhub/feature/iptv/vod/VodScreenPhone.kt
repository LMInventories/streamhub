package com.android.streamhub.feature.iptv.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.mediaCategoryArtFor
import com.android.streamhub.core.design.tvFocusBorder
import com.android.streamhub.feature.iptv.data.VodMovieInfo
import com.android.streamhub.feature.iptv.data.VodShowInfo

@Composable
fun VodScreenPhone(
    paddingValues: PaddingValues,
    onOpenLibrary: (VodMode) -> Unit,
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    viewModel: VodHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        VodHomeContent(
            uiState = uiState,
            onOpenLibrary = onOpenLibrary,
            onOpenMovie = onOpenMovie,
            onOpenShow = onOpenShow,
            modifier = Modifier.fillMaxSize().padding(paddingValues).statusBarsPadding(),
        )
    }
}

/**
 * Not private - reused as-is by VodScreenTv rather than a second TV-only implementation, same
 * reasoning as the old VodBrowseContent this replaces (and LiveTvScreenPhone's LiveTvBrowseContent).
 * A fixed 2-tile Movies/TV Shows hero row plus a "Recently Added" row for each, mirroring
 * Jellyfin/Emby's own Home screen shape - unlike Jellyfin's Home, this needs no Phone/Tv fork
 * since there's no focus-driven hero preview panel here, just a vertical stack of rows.
 */
@Composable
fun VodHomeContent(
    uiState: VodHomeUiState,
    onOpenLibrary: (VodMode) -> Unit,
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        uiState.errorMessage?.let { error ->
            Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp))
        }

        when {
            !uiState.hasSource -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No playlist added yet", color = Palette.TextPrimary)
                    Text(
                        text = "Add an Xtream Codes playlist from the Settings tab to browse movies and shows.",
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                    )
                }
            }

            !uiState.isSupported -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "VOD needs an Xtream Codes source. M3U playlists don't have a standard way to separate movies from live channels.",
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(32.dp),
                )
            }

            uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item(key = "hero") {
                    VodHeroRow(
                        entries = listOf(
                            VodHeroEntry("Movies", onClick = { onOpenLibrary(VodMode.MOVIES) }),
                            VodHeroEntry("TV Shows", onClick = { onOpenLibrary(VodMode.SHOWS) }),
                        ),
                    )
                }
                if (uiState.recentMovies.isNotEmpty()) {
                    item(key = "recent_movies") {
                        VodItemRow(
                            title = "Recently Added Movies",
                            movies = uiState.recentMovies,
                            onOpenMovie = onOpenMovie,
                            onSeeAll = { onOpenLibrary(VodMode.MOVIES) },
                        )
                    }
                }
                if (uiState.recentShows.isNotEmpty()) {
                    item(key = "recent_shows") {
                        VodItemRow(
                            title = "Recently Added TV Shows",
                            shows = uiState.recentShows,
                            onOpenShow = onOpenShow,
                            onSeeAll = { onOpenLibrary(VodMode.SHOWS) },
                        )
                    }
                }
            }
        }
    }
}

private data class VodHeroEntry(val label: String, val onClick: () -> Unit)

// One double-wide card per top-level bucket - fixed at Movies/TV Shows (not one per Xtream
// category, which are many and provider-defined) so this stays a stable jump-off point, same
// spirit as JellyfinHomeScreen's MediaRow but deliberately not derived from arbitrary category
// data. Data-driven off a list (not two hardcoded calls) so a Favourites tile can be appended
// later without any structural change here.
@Composable
private fun VodHeroRow(entries: List<VodHeroEntry>) {
    Column {
        Text(
            text = "Browse",
            color = Palette.TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { it.label }) { entry ->
                VodHeroTile(label = entry.label, onClick = entry.onClick)
            }
        }
    }
}

// mediaCategoryArtFor("Movies")/("TV Shows") both already resolve via its plain keyword-fallback
// branches, so every hero tile here always takes the art branch - the text fallback only exists
// because mediaCategoryArtFor is a general String -> Int? function, not because it's expected to
// fire for these two fixed labels.
@Composable
private fun VodHeroTile(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) VOD_FOCUSED_SCALE else 1f, label = "vodHeroTileFocusScale")
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(80.dp)
            .scale(scale)
            .clip(AppShapes.small)
            .background(Palette.Surface)
            .tvFocusBorder(interactionSource, AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val art = mediaCategoryArtFor(label)
        if (art != null) {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(text = label, color = Palette.TextPrimary)
        }
    }
}

@Composable
private fun VodItemRow(
    title: String,
    onSeeAll: () -> Unit,
    movies: List<VodMovieInfo> = emptyList(),
    shows: List<VodShowInfo> = emptyList(),
    onOpenMovie: (String) -> Unit = {},
    onOpenShow: (String) -> Unit = {},
) {
    Column {
        Text(
            text = title,
            color = Palette.TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // No horizontalArrangement spacing here - Poster already carries its own 4dp padding on
        // every side, so an additional row-level gap would double up between items.
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(movies, key = { it.id }) { movie ->
                Poster(
                    name = movie.name,
                    posterUrl = movie.posterUrl,
                    modifier = Modifier.width(112.dp),
                    onClick = { onOpenMovie(movie.id) },
                )
            }
            items(shows, key = { it.id }) { show ->
                Poster(
                    name = show.name,
                    posterUrl = show.posterUrl,
                    modifier = Modifier.width(112.dp),
                    onClick = { onOpenShow(show.id) },
                )
            }
            item(key = "see_all") { SeeAllTile(onClick = onSeeAll) }
        }
    }
}
