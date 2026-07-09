package com.android.streamhub.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.android.streamhub.core.design.AppShapes

@Composable
fun HomeScreenTv(
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dashboardEntries by viewModel.dashboardEntries.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = onSettingsClick, modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 0.dp)) {
            Text("Settings")
        }

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
                            .background(entry.accent.copy(alpha = 0.16f), AppShapes.large)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(entry.icon, contentDescription = null, tint = entry.accent, modifier = Modifier.size(36.dp))
                        Column {
                            Text(text = entry.title, color = Color.White)
                            Text(text = entry.subtitle, color = entry.accent)
                        }
                    }
                }
            }
        }
    }
}
