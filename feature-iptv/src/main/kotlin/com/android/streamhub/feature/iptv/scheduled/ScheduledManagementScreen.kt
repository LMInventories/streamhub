package com.android.streamhub.feature.iptv.scheduled

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledRecordingEntity
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledReminderEntity
import com.android.streamhub.feature.iptv.livetv.dateTimeFormatter
import com.android.streamhub.feature.iptv.livetv.rememberUse24HourTime
import java.time.Instant
import java.time.ZoneId

// Shared across phone and TV rather than split, same reasoning as RecordingsScreen - a plain
// list with cancel actions doesn't need orientation-specific structure, and wraps its own
// MaterialTheme since it's reachable from the TV nav host too.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledManagementScreen(
    onBack: () -> Unit,
    viewModel: ScheduledManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val use24Hour = rememberUse24HourTime()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Scheduled") },
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
                    uiState.reminders.isEmpty() && uiState.recordings.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nothing scheduled", color = Palette.TextPrimary)
                            Text(
                                text = "Reminders and recordings you set from the EPG guide or Search will show up here.",
                                color = Palette.TextMuted,
                                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                            )
                        }
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        if (uiState.reminders.isNotEmpty()) {
                            item { SectionHeader("Reminders") }
                            items(uiState.reminders, key = { "reminder:${it.id}" }) { reminder ->
                                ReminderRow(reminder, use24Hour, onCancel = { viewModel.cancelReminder(reminder) })
                            }
                        }
                        if (uiState.recordings.isNotEmpty()) {
                            item { SectionHeader("Scheduled Recordings") }
                            items(uiState.recordings, key = { "recording:${it.id}" }) { recording ->
                                RecordingRow(recording, use24Hour, onCancel = { viewModel.cancelRecording(recording) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Palette.TextPrimary,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ReminderRow(reminder: ScheduledReminderEntity, use24Hour: Boolean, onCancel: () -> Unit) {
    ScheduledRow(
        title = reminder.programTitle,
        subtitle = "${reminder.channelName} · ${formattedTime(reminder.programStartEpochSeconds, use24Hour)} · ${reminder.leadMinutes}m before",
        onCancel = onCancel,
    )
}

@Composable
private fun RecordingRow(recording: ScheduledRecordingEntity, use24Hour: Boolean, onCancel: () -> Unit) {
    ScheduledRow(
        title = recording.programTitle,
        subtitle = "${recording.channelName} · ${formattedTime(recording.recordStartEpochSeconds, use24Hour)}",
        onCancel = onCancel,
    )
}

private fun formattedTime(epochSeconds: Long, use24Hour: Boolean): String =
    dateTimeFormatter(use24Hour).format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

@Composable
private fun ScheduledRow(title: String, subtitle: String, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Palette.Error) }
    }
}
