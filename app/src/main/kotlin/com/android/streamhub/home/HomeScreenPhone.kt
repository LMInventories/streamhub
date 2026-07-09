package com.android.streamhub.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.design.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenPhone(
    paddingValues: PaddingValues,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dashboardEntries by viewModel.dashboardEntries.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TopAppBar(
                title = { Text("StreamHub") },
                modifier = Modifier.statusBarsPadding(),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(dashboardEntries, key = { it.route }) { entry ->
                    DashboardCard(entry = entry, onClick = { onNavigate(entry.route) })
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(entry: DashboardEntry, onClick: () -> Unit) {
    // ~75% of the previous square height at the same width (1f -> 1/0.75).
    Box(
        modifier = Modifier
            .aspectRatio(4f / 3f)
            .fillMaxWidth()
            .background(entry.accent.copy(alpha = 0.16f), AppShapes.large)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(entry.icon, contentDescription = null, tint = entry.accent, modifier = Modifier.size(32.dp))
            Column {
                Text(text = entry.title, color = Color.White)
                Text(text = entry.subtitle, color = entry.accent)
            }
        }
    }
}
