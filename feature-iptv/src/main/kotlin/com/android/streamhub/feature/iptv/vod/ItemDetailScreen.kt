package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.ui.phone.theme.appColorScheme

// Reachable from both the phone and TV nav hosts, and built with mobile Material3 components -
// TV's ambient theme is tv-material3's, not this one, so it wraps itself locally rather than
// relying on an ambient theme it can't count on. Same reasoning as EpgGridPanel/IptvSettingsScreen.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(if (uiState.isLoading) "" else uiState.title) },
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

                    else -> ItemDetailContent(uiState = uiState, onPlay = onPlay)
                }
            }
        }
    }
}

@Composable
private fun ItemDetailContent(uiState: ItemDetailUiState, onPlay: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(AppShapes.small),
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
                if (uiState.seriesName != null) {
                    Text(
                        text = buildString {
                            append(uiState.seriesName)
                            if (uiState.seasonNumber != null && uiState.episodeNumber != null) {
                                append(" · S${uiState.seasonNumber} E${uiState.episodeNumber}")
                            }
                        },
                        color = Palette.TextMuted,
                    )
                }
                DetailMetaRow(uiState)

                val resumeFraction = uiState.resumeFractionComplete
                Button(onClick = onPlay, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = if (resumeFraction != null) "Resume" else "Play", modifier = Modifier.padding(start = 6.dp))
                }
                if (resumeFraction != null) {
                    SignalBar(progress = resumeFraction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), segmentCount = 20)
                }
            }
        }

        uiState.plot?.let { plot ->
            Text(
                text = plot,
                color = Palette.TextPrimary,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
            uiState.cast?.let { InfoLine(label = "Cast", value = it) }
            uiState.director?.let { InfoLine(label = "Director", value = it) }
            uiState.genre?.let { InfoLine(label = "Genre", value = it) }
            uiState.releaseDate?.let { InfoLine(label = "Released", value = it) }
            uiState.rating?.let { InfoLine(label = "Rating", value = it) }
            uiState.duration?.let { InfoLine(label = "Duration", value = it) }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DetailMetaRow(uiState: ItemDetailUiState) {
    val parts = listOfNotNull(uiState.genre, uiState.duration, uiState.rating?.let { "★ $it" })
    if (parts.isNotEmpty()) {
        Text(text = parts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = "$label: ", color = Palette.TextMuted)
        Text(text = value, color = Palette.TextPrimary)
    }
}
