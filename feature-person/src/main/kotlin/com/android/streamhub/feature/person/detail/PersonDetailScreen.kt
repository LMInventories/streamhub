package com.android.streamhub.feature.person.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.phone.theme.appColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onOpenLibraryItem: (itemId: String, isSeries: Boolean) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        uiState.person == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                        }
                        else -> PersonDetailContent(uiState = uiState, onOpenLibraryItem = onOpenLibraryItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonDetailContent(
    uiState: PersonDetailUiState,
    onOpenLibraryItem: (String, Boolean) -> Unit,
) {
    val person = uiState.person ?: return
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(modifier = Modifier.width(110.dp).height(165.dp).clip(AppShapes.small)) {
                if (person.profileUrl != null) {
                    AsyncImage(model = person.profileUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = person.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge)
                val metaParts = listOfNotNull(person.birthday, person.placeOfBirth)
                if (metaParts.isNotEmpty()) {
                    Text(text = metaParts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        person.biography?.let { bio ->
            Text(text = bio, color = Palette.TextPrimary, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp))
        }

        if (uiState.movies.isNotEmpty()) {
            Text(text = "Movies", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            FilmographyRow(items = uiState.movies, onOpenLibraryItem = onOpenLibraryItem)
        }

        if (uiState.tvShows.isNotEmpty()) {
            Text(text = "TV Shows", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            FilmographyRow(items = uiState.tvShows, onOpenLibraryItem = onOpenLibraryItem)
        }

        // Required by TMDB's free-API terms.
        Text(
            text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 24.dp),
        )
    }
}

@Composable
private fun FilmographyRow(items: List<FilmographyItem>, onOpenLibraryItem: (String, Boolean) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.tmdbId }) { item -> FilmographyPoster(item = item, onOpenLibraryItem = onOpenLibraryItem) }
    }
}

@Composable
private fun FilmographyPoster(item: FilmographyItem, onOpenLibraryItem: (String, Boolean) -> Unit) {
    val matched = item.matchState as? LibraryMatchState.Matched
    Column(
        modifier = Modifier
            .width(110.dp)
            .let { if (matched != null) it.clickable { onOpenLibraryItem(matched.itemId, item.isSeries) } else it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(AppShapes.small)
                .alpha(if (matched != null) 1f else 0.6f),
        ) {
            if (item.posterUrl != null) {
                AsyncImage(model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
            }
        }
        Text(text = item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
        Text(
            text = if (item.matchState is LibraryMatchState.NotInLibrary) "Not in your library" else item.year?.toString().orEmpty(),
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
