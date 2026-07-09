package com.android.streamhub.feature.jellyfin.settings

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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.Palette

private val JellyfinHomeSectionOrderColorScheme = darkColorScheme(
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

/** Up/down buttons rather than drag-and-drop - no extra gesture-detection/reorder-animation state to build or get wrong, and moving a section a few positions is still just a few taps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinHomeSectionOrderScreen(
    onDone: () -> Unit,
    viewModel: JellyfinHomeSectionOrderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = JellyfinHomeSectionOrderColorScheme) {
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
