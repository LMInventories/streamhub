package com.android.streamhub.feature.emby.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsRowDivider
import com.android.streamhub.core.ui.tv.scaffold.TvSettingsTopBar
import com.android.streamhub.core.ui.tv.scaffold.rememberTvSettingsInitialFocus
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.Text as TvText

/** Up/down buttons rather than drag-and-drop - no extra gesture-detection/reorder-animation state to build or get wrong, and moving a section a few positions is still just a few taps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbyHomeSectionOrderScreen(
    onDone: () -> Unit,
    viewModel: EmbyHomeSectionOrderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Home screen order") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                return@Column
            }

            Text(
                text = "Sections with nothing in them right now still show up here, but won't appear on Home until they do.",
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.sections, key = { it.key }) { section ->
                    val index = uiState.sections.indexOf(section)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = section.title, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.moveUp(index) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { viewModel.moveDown(index) }, enabled = index < uiState.sections.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        }
    }
}

/** TV-native sibling of EmbyHomeSectionOrderScreen - same EmbyHomeSectionOrderViewModel and up/down-button reorder interaction. */
@Composable
fun EmbyHomeSectionOrderScreenTv(
    onDone: () -> Unit,
    viewModel: EmbyHomeSectionOrderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // readyKey = isLoading so this re-fires once the list actually has something to focus - a
    // plain one-shot Unit key would fire before the list exists and silently no-op forever.
    val firstRowFocusRequester = rememberTvSettingsInitialFocus(readyKey = uiState.isLoading)

    Column(modifier = Modifier.fillMaxSize()) {
        TvSettingsTopBar(title = "Home screen order", onBack = onDone)

        if (uiState.isLoading) return

        TvText(
            text = "Sections with nothing in them right now still show up here, but won't appear on Home until they do.",
            color = Palette.TextMuted,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(uiState.sections, key = { _, section -> section.key }) { index, section ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstRowFocusRequester) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvText(text = section.title, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                    TvIconButton(onClick = { viewModel.moveUp(index) }, enabled = index > 0) {
                        TvIcon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    TvIconButton(onClick = { viewModel.moveDown(index) }, enabled = index < uiState.sections.lastIndex) {
                        TvIcon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                    }
                }
                TvSettingsRowDivider()
            }
        }
    }
}
