package com.android.streamhub.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.R
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenPhone(
    paddingValues: PaddingValues,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dashboardEntries by viewModel.dashboardEntries.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = Palette.Background) {
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            HomeHeader()

            // Fixed(2) - buildDashboardEntries() is exactly the 4 sources (Live TV/VOD/Jellyfin/
            // Emby), so this always lands as a 2x2 grid.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(dashboardEntries, key = { it.route }) { entry ->
                    DashboardCard(entry = entry, onClick = { onNavigate(entry.route) })
                }
            }
        }
    }
}

/** Reserves the top of the dashboard for branding - just the app icon + name for now, ahead of a dedicated logo asset. */
@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
    ) {
        // R.mipmap.ic_launcher resolves to an <adaptive-icon> XML (background+foreground layers,
        // masked/composited by the OS at install time) - painterResource() only understands plain
        // vector/raster drawables and throws at runtime for that root element, crashing the app on
        // launch since Home is the start destination. Compositing the two underlying vector layers
        // manually here (both plain <vector> XML, safe for painterResource) gets the same look
        // without that crash.
        Box(modifier = Modifier.size(48.dp).clip(AppShapes.medium)) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(text = "StreamHub", color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text = "All your sources in one place", color = Palette.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DashboardCard(entry: DashboardEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .background(Palette.Surface, AppShapes.large)
            .border(1.dp, Palette.Border, AppShapes.large)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(entry.accent.copy(alpha = 0.16f)),
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = entry.accent,
                modifier = Modifier.size(26.dp).align(Alignment.Center),
            )
        }
        Column {
            Text(text = entry.title, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(text = entry.subtitle, color = Palette.TextMuted, fontSize = 13.sp)
        }
    }
}
