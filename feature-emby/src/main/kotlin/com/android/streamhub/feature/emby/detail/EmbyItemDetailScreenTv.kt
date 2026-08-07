package com.android.streamhub.feature.emby.detail

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.feature.emby.data.EmbyCastMember
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType

/** TV-native sibling of EmbyItemDetailScreen - same EmbyItemDetailViewModel, tv-material3 components in place of material3. */
@Composable
fun EmbyItemDetailScreenTv(
    itemId: String,
    onPlay: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EmbyItemDetailViewModel = hiltViewModel(),
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
        }

        // weight(1f) is load-bearing here, not decorative - see EmbyItemDetailScreen's matching
        // comment for why a plain fillMaxSize() second child here would overflow past the screen
        // and permanently hide however much of it the title Row's own height covers, unreachable
        // by scrolling.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.item == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                }
                else -> EmbyItemDetailContentTv(item = uiState.item!!, onPlay = { onPlay(itemId) })
            }
        }
    }
}

@Composable
private fun EmbyItemDetailContentTv(item: EmbyItemInfo, onPlay: () -> Unit) {
    val isEpisode = item.type == EmbyItemType.EPISODE
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp, 16.dp)) {
                // Episodes get a small 16:9 scene-thumbnail instead of the narrow 2:3 poster -
                // see the phone screen's matching comment for why.
                Box(
                    modifier = (if (isEpisode) Modifier.width(200.dp).aspectRatio(16f / 9f) else Modifier.width(140.dp).height(210.dp))
                        .clip(AppShapes.small),
                ) {
                    val imageUrl = if (isEpisode) item.episodeThumbnailUrl ?: item.primaryImageUrl else item.primaryImageUrl
                    if (imageUrl != null) {
                        AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                    }
                }

                Column(modifier = Modifier.padding(start = 20.dp)) {
                    if (isEpisode && item.seriesName != null) {
                        Text(text = item.seriesName, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = buildString {
                                if (item.parentIndexNumber != null && item.indexNumber != null) {
                                    append("Season ${item.parentIndexNumber} · Episode ${item.indexNumber} - ")
                                }
                                append(item.name)
                            },
                            color = Palette.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    } else {
                        Text(text = item.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
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
                    Button(onClick = onPlay, modifier = Modifier.padding(top = 16.dp).focusRequester(playFocusRequester)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = if (resumeFraction != null) "Resume" else "Play", modifier = Modifier.padding(start = 6.dp))
                    }
                    if (resumeFraction != null) {
                        SignalBar(progress = resumeFraction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), segmentCount = 20)
                    }
                }
            }
        }

        item.overview?.let { overview ->
            item {
                Text(
                    text = overview,
                    color = Palette.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }

        if (item.genres.isNotEmpty()) {
            item {
                Text(
                    text = "Genres: ${item.genres.joinToString(", ")}",
                    color = Palette.TextMuted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp, 4.dp),
                )
            }
        }

        if (item.cast.isNotEmpty()) {
            item {
                Text(text = "Cast", color = Palette.TextPrimary, modifier = Modifier.padding(24.dp, 12.dp, 24.dp, 8.dp))
            }
            item {
                EmbyCastRowTv(cast = item.cast)
            }
        }
    }
}

/** Shared cast row for both TV detail screens - tv-material3 Card equivalent of the phone EmbyCastMemberCard. No image (EmbyCastMember carries no image URL), so the Card's content is always the plain placeholder box. Not private - reused as-is by EmbySeriesDetailScreenTv (same package). */
@Composable
fun EmbyCastRowTv(cast: List<EmbyCastMember>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cast, key = { it.id }) { member ->
            Column(modifier = Modifier.width(100.dp)) {
                Card(onClick = {}, modifier = Modifier.size(100.dp)) {
                    Box(modifier = Modifier.fillMaxSize().clip(AppShapes.small).background(Palette.Surface))
                }
                Text(text = member.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
                member.role?.let { role ->
                    Text(text = role, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
