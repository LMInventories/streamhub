package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.streamhub.core.design.AppFonts
import com.android.streamhub.core.design.AppTextStyles
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.SignalBar
import com.android.streamhub.feature.iptv.data.EpgProgram
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * BasicText/plain TextStyle rather than material3/tv-material3's Text+MaterialTheme - this sits
 * directly on the mini-player's dark background in both the phone and TV screens, and those two
 * themes don't share CompositionLocals, so relying on either would look wrong (or unreadable)
 * in the other.
 */
@Composable
fun EpgInfoPanel(
    channelName: String?,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BasicText(
            text = channelName ?: "Select a channel to preview",
            style = TextStyle(color = Palette.TextPrimary, fontFamily = AppFonts.Display, fontSize = AppTextStyles.titleSmall.fontSize, fontWeight = AppTextStyles.headlineSmall.fontWeight),
        )
        if (nowProgram != null) {
            BasicText(
                text = "Now (${nowProgram.timeRange()}): ${nowProgram.title}",
                style = TextStyle(color = Palette.TextPrimary, fontFamily = AppFonts.Mono, fontSize = AppTextStyles.bodySmall.fontSize),
                modifier = Modifier.padding(top = 4.dp),
            )
            SignalBar(
                progress = nowProgram.elapsedFraction(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                segmentCount = 20,
            )
            nowProgram.description?.let { description ->
                BasicText(
                    text = description,
                    style = TextStyle(color = Palette.TextMuted, fontFamily = AppFonts.Body, fontSize = AppTextStyles.bodySmall.fontSize),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        if (nextProgram != null) {
            BasicText(
                text = "Next (${nextProgram.timeRange()}): ${nextProgram.title}",
                style = TextStyle(color = Palette.TextMuted, fontFamily = AppFonts.Mono, fontSize = AppTextStyles.labelMedium.fontSize),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun EpgProgram.timeRange(): String {
    val zone = ZoneId.systemDefault()
    val start = timeFormatter.format(startAt.atZone(zone))
    val end = timeFormatter.format(endAt.atZone(zone))
    return "$start-$end"
}

private fun EpgProgram.elapsedFraction(now: Instant = Instant.now()): Float {
    val total = (endAt.epochSecond - startAt.epochSecond).coerceAtLeast(1)
    val elapsed = (now.epochSecond - startAt.epochSecond).coerceIn(0, total)
    return elapsed.toFloat() / total.toFloat()
}
