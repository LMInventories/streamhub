package com.android.streamhub.feature.emby.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.emby.data.EmbyCastMember
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyItemType

/**
 * Phone movie/episode detail screen for Emby - deliberately lean next to
 * JellyfinItemDetailScreen: no download button, no favorite/watched toggle, no audio/subtitle/
 * version picker section (see EmbyItemDetailViewModel's own doc for why - all out of scope this
 * pass). onPlay only carries [itemId] back out to the nav host, which wraps it into
 * Route.playerRoute(itemId, SourceType.EMBY) - this module has no reason to know about that route
 * shape itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbyItemDetailScreen(
    itemId: String,
    onPlay: (itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: EmbyItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    // No title here on purpose - the item's name is already the bold heading in
                    // the content below. Mirrors JellyfinItemDetailScreen's own reasoning.
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                )

                // weight(1f) is load-bearing here, not decorative - without it, the
                // fillMaxSize() content below would be measured against the whole Column's
                // height (same budget TopAppBar already consumed from), not what's actually left
                // after it, and would get placed starting below TopAppBar while still claiming
                // full-screen height - pushing its own bottom edge that same distance past the
                // visible screen, with no way to scroll the hidden portion back into view.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        uiState.item == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.errorMessage ?: "Not found", color = Palette.Error, modifier = Modifier.padding(32.dp))
                        }
                        else -> EmbyItemDetailContent(item = uiState.item!!, onPlay = { onPlay(itemId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbyItemDetailContent(item: EmbyItemInfo, onPlay: () -> Unit) {
    val isEpisode = item.type == EmbyItemType.EPISODE
    // Grabs initial D-pad focus on entry so a TV remote's first OK press does the obvious thing
    // (start playback) instead of landing wherever the focus system defaults to - harmless on
    // touch devices, which have no focus ring to show. Mirrors JellyfinItemDetailScreen.
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Episodes get a small 16:9 scene-thumbnail instead of the narrow 2:3 poster - a
            // poster-shaped box for an episode would show a stretched scene-grab or nothing at
            // all, neither of which reads as intentional the way a proper 16:9 thumbnail does.
            Box(
                modifier = (if (isEpisode) Modifier.width(110.dp).aspectRatio(16f / 9f) else Modifier.width(120.dp).height(180.dp))
                    .clip(AppShapes.small),
            ) {
                val imageUrl = if (isEpisode) item.episodeThumbnailUrl ?: item.primaryImageUrl else item.primaryImageUrl
                if (imageUrl != null) {
                    AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Palette.Surface))
                }
            }

            Column(modifier = Modifier.padding(start = 16.dp)) {
                if (isEpisode && item.seriesName != null) {
                    Text(text = item.seriesName, color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = buildString {
                            if (item.parentIndexNumber != null && item.indexNumber != null) {
                                append("Season ${item.parentIndexNumber} · Episode ${item.indexNumber} - ")
                            }
                            append(item.name)
                        },
                        color = Palette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    Text(text = item.name, color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                val metaParts = listOfNotNull(
                    item.productionYear?.toString(),
                    item.runtimeMinutes?.let { "$it min" },
                    item.communityRating?.let { "★ %.1f".format(it) },
                )
                if (metaParts.isNotEmpty()) {
                    Text(text = metaParts.joinToString(" · "), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
                }

                // Label says "Resume" whenever the server has a played-percentage/resume point
                // recorded for this item, "Play" otherwise - same rule JellyfinItemDetailScreen
                // uses (item.playedPercentage > 0f), just without that file's own subtitle/audio/
                // version picker choices threaded through onPlay.
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

        item.overview?.let { overview ->
            Text(
                text = overview,
                color = Palette.TextPrimary,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium,
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
                items(item.cast, key = { it.id }) { member -> EmbyCastMemberCard(member) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Cast card with name/role text only - EmbyCastMember has no image URL field (unlike JellyfinCastMember), so this always shows the same plain placeholder box no AsyncImage branch would ever pick. Public - reused as-is by EmbySeriesDetailScreen (same package). */
@Composable
fun EmbyCastMemberCard(member: EmbyCastMember) {
    Column(modifier = Modifier.width(90.dp)) {
        Box(modifier = Modifier.size(90.dp).clip(AppShapes.small).background(Palette.Surface))
        Text(text = member.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextPrimary, modifier = Modifier.padding(top = 4.dp))
        member.role?.let { role ->
            Text(text = role, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.TextMuted)
        }
    }
}
