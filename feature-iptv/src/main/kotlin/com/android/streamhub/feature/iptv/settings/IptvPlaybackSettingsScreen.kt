package com.android.streamhub.feature.iptv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.design.PillToggle
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.iptv.data.ChannelSortOrder
import com.android.streamhub.feature.iptv.data.PreviewPlayerSize

private val SORT_OPTIONS = listOf(ChannelSortOrder.PLAYLIST to "Playlist order", ChannelSortOrder.ALPHABETICAL to "A-Z")
private val TIME_FORMAT_OPTIONS = listOf(true to "24-hour", false to "12-hour")
private val PREVIEW_SIZE_OPTIONS = PreviewPlayerSize.entries.toList()

/** Same "wrap own MaterialTheme locally" reasoning as IptvSettingsScreen - reachable from the TV nav host too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvPlaybackSettingsScreen(
    onDone: () -> Unit,
    viewModel: IptvPlaybackSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Live TV playback") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Resume last channel", color = Palette.TextPrimary)
                        Text("Automatically preview whatever was on when you left", color = Palette.TextMuted)
                    }
                    Switch(checked = uiState.resumeLastChannel, onCheckedChange = viewModel::setResumeLastChannel)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Channel order", color = Palette.TextMuted)
                    PillToggle(
                        options = SORT_OPTIONS.map { it.second },
                        selectedIndex = SORT_OPTIONS.indexOfFirst { it.first == uiState.channelSortOrder }.coerceAtLeast(0),
                        onSelect = { index -> viewModel.setChannelSortOrder(SORT_OPTIONS[index].first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("EPG time format", color = Palette.TextMuted)
                    PillToggle(
                        options = TIME_FORMAT_OPTIONS.map { it.second },
                        selectedIndex = TIME_FORMAT_OPTIONS.indexOfFirst { it.first == uiState.use24HourTime }.coerceAtLeast(0),
                        onSelect = { index -> viewModel.setUse24HourTime(TIME_FORMAT_OPTIONS[index].first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preview player size", color = Palette.TextMuted)
                    PillToggle(
                        options = PREVIEW_SIZE_OPTIONS.map { it.label },
                        selectedIndex = PREVIEW_SIZE_OPTIONS.indexOf(uiState.previewPlayerSize).coerceAtLeast(0),
                        onSelect = { index -> viewModel.setPreviewPlayerSize(PREVIEW_SIZE_OPTIONS[index]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
