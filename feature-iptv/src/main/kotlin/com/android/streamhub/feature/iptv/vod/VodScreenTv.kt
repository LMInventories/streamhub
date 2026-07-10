package com.android.streamhub.feature.iptv.vod

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.ui.phone.theme.appColorScheme

/**
 * Reuses VodScreenPhone's own VodBrowseContent rather than a second, separately-maintained TV
 * layout - same reasoning as LiveTvScreenTv. Wraps its own Material3 MaterialTheme since the
 * shared content uses Material3 components (ListItem, DropdownMenu, ...) and the ambient theme
 * from TvNavHost is tv-material3's, not this one.
 */
@Composable
fun VodScreenTv(
    onOpenMovie: (itemId: String) -> Unit,
    onOpenShow: (seriesId: String) -> Unit,
    viewModel: VodViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = appColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            VodBrowseContent(
                uiState = uiState,
                onSelectCategory = viewModel::selectCategory,
                onClearCategorySelection = viewModel::clearCategorySelection,
                onSetMode = viewModel::setMode,
                onSetGridColumns = viewModel::setGridColumns,
                onOpenMovie = onOpenMovie,
                onOpenShow = onOpenShow,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )
        }
    }
}
