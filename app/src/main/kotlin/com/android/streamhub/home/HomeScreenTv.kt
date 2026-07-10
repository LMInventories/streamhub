package com.android.streamhub.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette

@Composable
fun HomeScreenTv(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dashboardEntries by viewModel.dashboardEntries.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(dashboardEntries, key = { it.route }) { entry ->
                Card(onClick = { onNavigate(entry.route) }) {
                    // ~75% of the previous height at the same width (1.6f -> 1.6f/0.75f).
                    Column(
                        modifier = Modifier
                            .aspectRatio(1.6f / 0.75f)
                            .background(Palette.Surface, AppShapes.large)
                            .border(1.dp, Palette.Border, AppShapes.large)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(entry.accent.copy(alpha = 0.16f))) {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                tint = entry.accent,
                                modifier = Modifier.size(28.dp).align(Alignment.Center),
                            )
                        }
                        Column {
                            Text(text = entry.title, color = Palette.TextPrimary)
                            Text(text = entry.subtitle, color = Palette.TextMuted)
                        }
                    }
                }
            }
        }
    }
}
