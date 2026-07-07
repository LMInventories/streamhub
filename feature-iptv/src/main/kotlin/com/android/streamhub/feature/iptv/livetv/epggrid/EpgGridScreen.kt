package com.android.streamhub.feature.iptv.livetv.epggrid

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val PIXELS_PER_MINUTE = 2.dp
private val CHANNEL_LABEL_WIDTH = 120.dp
private val ROW_HEIGHT = 64.dp
private val hourFormatter = DateTimeFormatter.ofPattern("EEE HH:mm")

// This screen uses mobile Material3 chrome (Surface/TopAppBar/HorizontalDivider) but is reachable
// from both the phone AND TV nav hosts. The TV host only wraps content in tv-material3's
// MaterialTheme, not this one, so without a local wrapper these components would fall back to
// M3's default light scheme when opened from TV - same reasoning as PlayerScreenTv's dialogs
// wrapping themselves in their own MaterialTheme rather than relying on an ambient one.
private val EpgGridColorScheme = darkColorScheme(
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

/**
 * One shared screen rather than phone/TV variants - this is a dense data-table-style view where
 * the two form factors mostly differ in input method (touch-scroll vs D-pad), not layout, so a
 * TV-specific rebuild wasn't worth the scope here the way the browse/player screens were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgGridScreen(
    onBack: () -> Unit,
    viewModel: EpgGridViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // One ScrollState shared by the time header and every channel row's timeline - dragging any
    // of them updates this same value, which is what keeps them all in sync horizontally without
    // a bespoke multi-list-state solution.
    val sharedScrollState = rememberScrollState()

    MaterialTheme(colorScheme = EpgGridColorScheme) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("TV Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            uiState.errorMessage?.let { error ->
                Text(text = error, color = Palette.Error, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp))
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(CHANNEL_LABEL_WIDTH))
                    Box(modifier = Modifier.horizontalScroll(sharedScrollState)) {
                        TimeHeader(windowStart = uiState.windowStart, windowEnd = uiState.windowEnd)
                    }
                }
                HorizontalDivider()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.channels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            programmes = uiState.programmesByChannel[channel.id].orEmpty(),
                            windowStart = uiState.windowStart,
                            windowEnd = uiState.windowEnd,
                            sharedScrollState = sharedScrollState,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun TimeHeader(windowStart: Instant, windowEnd: Instant) {
    val hours = ChronoUnit.HOURS.between(windowStart, windowEnd).toInt()
    val zone = ZoneId.systemDefault()
    Row {
        repeat(hours) { hourIndex ->
            val hourInstant = windowStart.plus(hourIndex.toLong(), ChronoUnit.HOURS)
            Box(modifier = Modifier.width(PIXELS_PER_MINUTE * 60)) {
                Text(
                    text = hourFormatter.format(hourInstant.atZone(zone)),
                    color = Palette.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: IptvChannelInfo,
    programmes: List<EpgProgram>,
    windowStart: Instant,
    windowEnd: Instant,
    sharedScrollState: ScrollState,
) {
    Row(modifier = Modifier.height(ROW_HEIGHT)) {
        Box(
            modifier = Modifier.width(CHANNEL_LABEL_WIDTH).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = channel.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
        Box(modifier = Modifier.horizontalScroll(sharedScrollState).fillMaxHeight()) {
            ChannelTimeline(programmes = programmes, windowStart = windowStart, windowEnd = windowEnd)
        }
    }
}

@Composable
private fun ChannelTimeline(programmes: List<EpgProgram>, windowStart: Instant, windowEnd: Instant) {
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
                    .background(Palette.Surface, AppShapes.small)
                    .padding(6.dp),
            ) {
                Text(
                    text = program.title,
                    color = Palette.TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
