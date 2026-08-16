package com.android.streamhub.feature.person.detail

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.label

/** TV-native sibling of PersonDetailScreen - same PersonDetailViewModel, tv-material3 Card-based carousels for D-pad focus traversal, mirroring TvCastRow/JellyfinPosterTv's own Card usage. */
@Composable
fun PersonDetailScreenTv(
    onBack: () -> Unit,
    onOpenLibraryItem: (itemId: String, isSeries: Boolean, sourceType: SourceType) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.person == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                }
                else -> PersonDetailContentTv(uiState = uiState, onOpenLibraryItem = onOpenLibraryItem)
            }
        }
    }
}

@Composable
private fun PersonDetailContentTv(
    uiState: PersonDetailUiState,
    onOpenLibraryItem: (String, Boolean, SourceType) -> Unit,
) {
    val person = uiState.person ?: return
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp, 8.dp)) {
                Box(modifier = Modifier.width(140.dp).height(210.dp).clip(AppShapes.small)) {
                    if (person.profileUrl != null) {
                        AsyncImage(model = person.profileUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                    }
                }
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Text(text = person.name, color = Palette.TextPrimary, style = MaterialTheme.typography.headlineSmall)
                    val metaParts = listOfNotNull(person.birthday, person.placeOfBirth)
                    if (metaParts.isNotEmpty()) {
                        Text(text = metaParts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                    person.biography?.let { bio ->
                        Text(text = bio, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        if (uiState.movies.isNotEmpty()) {
            item {
                Text(text = "Movies", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                FilmographyRowTv(items = uiState.movies, requestedSourceType = uiState.requestedSourceType, onOpenLibraryItem = onOpenLibraryItem)
            }
        }

        if (uiState.tvShows.isNotEmpty()) {
            item {
                Text(text = "TV Shows", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                FilmographyRowTv(items = uiState.tvShows, requestedSourceType = uiState.requestedSourceType, onOpenLibraryItem = onOpenLibraryItem)
            }
        }

        item {
            // Required by TMDB's free-API terms.
            Text(
                text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(24.dp, 16.dp, 24.dp, 8.dp),
            )
        }
    }
}

@Composable
private fun FilmographyRowTv(items: List<FilmographyItem>, requestedSourceType: SourceType, onOpenLibraryItem: (String, Boolean, SourceType) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.tmdbId }) { item ->
            FilmographyPosterTv(item = item, requestedSourceType = requestedSourceType, onOpenLibraryItem = onOpenLibraryItem)
        }
    }
}

@Composable
private fun FilmographyPosterTv(item: FilmographyItem, requestedSourceType: SourceType, onOpenLibraryItem: (String, Boolean, SourceType) -> Unit) {
    val matched = item.matchState as? LibraryMatchState.Matched
    Column(modifier = Modifier.width(120.dp)) {
        Card(
            onClick = { matched?.let { onOpenLibraryItem(it.itemId, item.isSeries, it.sourceType) } },
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(AppShapes.small).alpha(if (matched != null) 1f else 0.6f)) {
                if (item.posterUrl != null) {
                    AsyncImage(model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }
        }
        Text(text = item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
        Text(
            text = when {
                item.matchState is LibraryMatchState.NotInLibrary -> "Not in your library"
                matched != null && matched.sourceType != requestedSourceType -> "On ${matched.sourceType.label()}"
                else -> item.year?.toString().orEmpty()
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
