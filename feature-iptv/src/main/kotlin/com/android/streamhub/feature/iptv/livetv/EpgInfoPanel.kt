package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.android.streamhub.feature.iptv.data.EpgProgram
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Plain white/gray text rather than MaterialTheme.typography/colorScheme - this sits directly
 * on the mini-player's dark background in both the phone (material3) and TV (tv-material)
 * screens, and those two themes don't share CompositionLocals, so relying on either would look
 * wrong (or unreadable) in the other.
 */
@Composable
fun EpgInfoPanel(
    channelName: String?,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = channelName ?: "Select a channel to preview",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (nowProgram != null) {
            Text(text = "Now (${nowProgram.timeRange()}): ${nowProgram.title}", color = Color.White, fontSize = 13.sp)
        }
        if (nextProgram != null) {
            Text(text = "Next (${nextProgram.timeRange()}): ${nextProgram.title}", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

private fun EpgProgram.timeRange(): String {
    val zone = ZoneId.systemDefault()
    val start = timeFormatter.format(startAt.atZone(zone))
    val end = timeFormatter.format(endAt.atZone(zone))
    return "$start-$end"
}
