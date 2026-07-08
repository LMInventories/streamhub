package com.android.streamhub.feature.jellyfin.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.darkColorScheme
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
import com.android.streamhub.feature.jellyfin.data.JellyfinCastMember
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo

private val JellyfinDetailColorScheme = darkColorScheme(
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
fun JellyfinItemDetailScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    viewModel: JellyfinItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = JellyfinDetailColorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(uiState.item?.name.orEmpty()) },
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
                    uiState.item == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                    }
                    else -> JellyfinItemDetailContent(item = uiState.item!!, onPlay = onPlay)
                }
            }
        }
    }
}

@Composable
private fun JellyfinItemDetailContent(item: JellyfinItemInfo, onPlay: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(modifier = Modifier.width(120.dp).height(180.dp).clip(AppShapes.small)) {
                if (item.primaryImageUrl != null) {
                    AsyncImage(
                        model = item.primaryImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
                if (item.seriesName != null) {
                    Text(
                        text = buildString {
                            append(item.seriesName)
                            if (item.parentIndexNumber != null && item.indexNumber != null) {
                                append(" · S${item.parentIndexNumber} E${item.indexNumber}")
                            }
                        },
                        color = Palette.TextMuted,
                    )
                }
                val metaParts = listOfNotNull(
                    item.productionYear?.toString(),
                    item.runtimeMinutes?.let { "$it min" },
                    item.communityRating?.let { "★ %.1f".format(it) },
                )
                if (metaParts.isNotEmpty()) {
                    Text(text = metaParts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }

                val resumeFraction = item.playedPercentage?.takeIf { it > 0f }?.div(100f)
                Button(onClick = onPlay, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = if (resumeFraction != null) "Resume" else "Play", modifier = Modifier.padding(start = 6.dp))
                }
                if (resumeFraction != null) {
                    SignalBar(progress = resumeFraction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), segmentCount = 20)
                }
            }
        }

        item.overview?.let { overview ->
            Text(
                text = overview,
                color = Palette.TextPrimary,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            )
        }

        if (item.genres.isNotEmpty()) {
            Text(
                text = "Genres: ${item.genres.joinToString(", ")}",
                color = Palette.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            )
        }

        if (item.cast.isNotEmpty()) {
            Text(text = "Cast", color = Palette.TextPrimary, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(item.cast, key = { it.id }) { member -> CastMemberCard(member) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CastMemberCard(member: JellyfinCastMember) {
    Column(modifier = Modifier.width(90.dp)) {
        Box(modifier = Modifier.size(90.dp).clip(AppShapes.small)) {
            if (member.imageUrl != null) {
                AsyncImage(
                    model = member.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
            }
        }
        Text(text = member.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
        member.role?.let { role ->
            Text(text = role, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextMuted)
        }
    }
}
