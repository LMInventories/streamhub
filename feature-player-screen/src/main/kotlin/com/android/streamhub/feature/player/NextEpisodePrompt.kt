package com.android.streamhub.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.streamhub.core.common.domain.NextPlaybackItem
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette

/** How long before the end of an episode the "Next Episode" prompt appears - also the number it counts down from. */
const val NEXT_EPISODE_PROMPT_LEAD_MS = 10_000L

/**
 * Whole seconds left before the next episode auto-starts, or null when the prompt shouldn't be up
 * at all. Derived from the real playback position rather than an independent timer so the two can
 * never disagree: seeking backwards out of the window dismisses the prompt, seeking forwards into
 * it brings the prompt back, and pausing freezes the countdown (PlayerController stops its position
 * ticker while paused), which is what a viewer pausing at the credits obviously expects.
 */
fun nextEpisodeCountdownSeconds(positionMs: Long, durationMs: Long): Int? {
    if (durationMs <= 0L) return null
    val remainingMs = durationMs - positionMs
    if (remainingMs > NEXT_EPISODE_PROMPT_LEAD_MS) return null
    val leadSeconds = (NEXT_EPISODE_PROMPT_LEAD_MS / 1000L).toInt()
    // Rounds up so a full ten seconds remaining reads "10" rather than briefly flashing "9".
    return ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, leadSeconds)
}

/**
 * The shared visual body of the prompt - image, show/episode identity, description and countdown.
 * Form-factor-agnostic on purpose: the phone and TV screens wrap this with their own buttons
 * (Material3 vs tv-material3, which can't be mixed) and their own placement, but nothing about the
 * card's content differs between them, so it lives here once instead of being copy-pasted.
 */
@Composable
fun NextEpisodePromptBody(
    nextItem: NextPlaybackItem,
    secondsRemaining: Int,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(AppShapes.large)
            .background(Palette.Surface)
            .padding(16.dp),
    ) {
        Box(modifier = Modifier.width(160.dp).aspectRatio(16f / 9f).clip(AppShapes.small)) {
            if (nextItem.thumbnailUrl != null) {
                AsyncImage(
                    model = nextItem.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Palette.Border))
            }
        }

        Column(modifier = Modifier.width(320.dp).padding(start = 16.dp)) {
            NextEpisodeText(
                text = "Next episode in $secondsRemaining…",
                color = Palette.Accent,
                weight = FontWeight.Bold,
            )
            nextItem.seriesName?.let { seriesName ->
                NextEpisodeText(text = seriesName, color = Palette.TextMuted, topPadding = 6)
            }
            NextEpisodeText(
                text = listOfNotNull(nextItem.episodeLabel, nextItem.title.takeIf { it.isNotBlank() })
                    .joinToString(" - "),
                color = Palette.TextPrimary,
                weight = FontWeight.Bold,
                maxLines = 2,
                topPadding = 2,
            )
            nextItem.description?.takeIf { it.isNotBlank() }?.let { description ->
                NextEpisodeText(text = description, color = Palette.TextMuted, maxLines = 3, topPadding = 6)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                buttons()
            }
        }
    }
}

/**
 * Plain BasicText rather than either Material3's or tv-material3's Text - this composable is shared
 * by both form factors, and each of those Texts reads a theme ambient the other screen doesn't
 * provide. Colors come from Palette either way, so nothing is lost by not going through a theme.
 */
@Composable
private fun NextEpisodeText(
    text: String,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    topPadding: Int = 0,
) {
    BasicText(
        text = text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontWeight = weight),
        modifier = Modifier.padding(top = topPadding.dp),
    )
}

/** Full-screen dimmed layer the prompt card sits on, so the card reads as a modal over the video rather than a floating sticker. */
@Composable
fun NextEpisodePromptScrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
