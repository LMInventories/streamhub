package com.android.streamhub.feature.iptv.livetv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val PIXELS_PER_MINUTE = 2.dp
private val CHANNEL_LABEL_WIDTH = 110.dp
private val ROW_HEIGHT = 56.dp
private val hourFormatter = DateTimeFormatter.ofPattern("EEE HH:mm")

/**
 * Landscape-only inline EPG grid (not a separate screen) - channel labels fixed in a left
 * column, program blocks positioned by absolute offset (robust to real-world gaps/overlaps in
 * provider data) inside a horizontally-scrolling timeline. One shared ScrollState across the
 * time header and every row keeps them all scrolling in sync.
 *
 * Uses BasicText/SignalBar rather than material3's Text/LinearProgressIndicator+MaterialTheme -
 * this is shared by both the phone (landscape-only) and TV (always) Live TV screens, and TV's
 * ambient theme is tv-material3's, not this one, so relying on it here would render wrong.
 */
@Composable
fun EpgGridPanel(
    channels: List<IptvChannelInfo>,
    programmesByChannel: Map<String, List<EpgProgram>>,
    windowStart: Instant,
    windowEnd: Instant,
    isLoading: Boolean,
    loadProgress: Float?,
    onFocusChannel: (IptvChannelInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedScrollState = rememberScrollState()

    Column(modifier = modifier) {
        // isLoading alone (no real progress yet) covers the gap loadProgress leaves - a Room
        // read, a cache hit, or a download too fast to catch a callback - where the grid would
        // otherwise render as an empty timeline with no explanation that anything's happening.
        if (loadProgress != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                BasicText(
                    text = "Loading guide… ${(loadProgress * 100).toInt()}%",
                    style = TextStyle(color = Palette.TextMuted, fontSize = 12.sp),
                )
                SignalBar(
                    progress = loadProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    segmentCount = 32,
                )
            }
        } else if (isLoading) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                BasicText(
                    text = "Loading guide…",
                    style = TextStyle(color = Palette.TextMuted, fontSize = 12.sp),
                )
                IndeterminateSignalBar(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(CHANNEL_LABEL_WIDTH))
            Box(modifier = Modifier.horizontalScroll(sharedScrollState)) {
                TimeHeader(windowStart = windowStart, windowEnd = windowEnd)
            }
        }
        HorizontalDivider(color = Palette.Border)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(channels, key = { it.id }) { channel ->
                GridChannelRow(
                    channel = channel,
                    programmes = programmesByChannel[channel.id].orEmpty(),
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    sharedScrollState = sharedScrollState,
                    onClick = { onFocusChannel(channel) },
                )
                HorizontalDivider(color = Palette.Border)
            }
        }
    }
}

@Composable
private fun IndeterminateSignalBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "epg-loading")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "epg-loading-progress",
    )
    SignalBar(progress = progress, modifier = modifier, segmentCount = 32)
}

@Composable
private fun TimeHeader(windowStart: Instant, windowEnd: Instant) {
    val hours = ChronoUnit.HOURS.between(windowStart, windowEnd).toInt()
    val zone = ZoneId.systemDefault()
    Row {
        repeat(hours) { hourIndex ->
            val hourInstant = windowStart.plus(hourIndex.toLong(), ChronoUnit.HOURS)
            Box(modifier = Modifier.width(PIXELS_PER_MINUTE * 60)) {
                BasicText(
                    text = hourFormatter.format(hourInstant.atZone(zone)),
                    style = TextStyle(color = Palette.TextMuted, fontSize = 11.sp),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GridChannelRow(
    channel: IptvChannelInfo,
    programmes: List<EpgProgram>,
    windowStart: Instant,
    windowEnd: Instant,
    sharedScrollState: ScrollState,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.height(ROW_HEIGHT)) {
        Box(
            modifier = Modifier.width(CHANNEL_LABEL_WIDTH).fillMaxHeight()
                .background(Palette.Surface)
                .clickable(onClick = onClick)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                text = channel.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Palette.TextPrimary, fontSize = 12.sp),
                modifier = Modifier.padding(4.dp),
            )
        }
        Box(
            modifier = Modifier
                .horizontalScroll(sharedScrollState)
                .fillMaxHeight(),
        ) {
            ChannelTimeline(programmes = programmes, windowStart = windowStart, windowEnd = windowEnd)
        }
    }
}

@Composable
private fun ChannelTimeline(
    programmes: List<EpgProgram>,
    windowStart: Instant,
    windowEnd: Instant,
) {
    val totalMinutes = ChronoUnit.MINUTES.between(windowStart, windowEnd).toInt()

    Box(modifier = Modifier.width(PIXELS_PER_MINUTE * totalMinutes).fillMaxHeight()) {
        programmes.forEach { program ->
            val clampedStart = maxOf(program.startAt, windowStart)
            val clampedEnd = minOf(program.endAt, windowEnd)
            if (clampedEnd <= clampedStart) return@forEach

            val offsetMinutes = ChronoUnit.MINUTES.between(windowStart, clampedStart).toInt()
            val durationMinutes = ChronoUnit.MINUTES.between(clampedStart, clampedEnd).toInt().coerceAtLeast(1)
            val blockWidth: Dp = (PIXELS_PER_MINUTE * durationMinutes) - 2.dp

            Box(
                modifier = Modifier
                    .offset(x = PIXELS_PER_MINUTE * offsetMinutes)
                    .width(blockWidth.coerceAtLeast(4.dp))
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
                    .background(Palette.SurfaceElevated, AppShapes.small)
                    .padding(6.dp),
            ) {
                BasicText(
                    text = program.title,
                    style = TextStyle(color = Palette.TextPrimary, fontSize = 11.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
