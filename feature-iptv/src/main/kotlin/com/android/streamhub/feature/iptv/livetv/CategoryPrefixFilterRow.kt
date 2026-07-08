package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.iptv.data.IptvCategoryInfo

/**
 * Xtream/M3U category lists are often huge and repetitively prefixed by provider convention
 * ("UK| Entertainment", "UK| Sports", "USA| News", "24/7 MOVIES", ...) - scrolling through all
 * of them to find one region/type is unreasonable, so this derives a quick-filter chip per
 * distinct prefix instead of requiring any server-side metadata. The heuristic: everything up to
 * the first |, :, - or _ (common provider separators), then just the first whitespace-token of
 * that if there was no such separator - matches "UK| X" -> "UK", "24/7 MOVIES" -> "24/7",
 * "USA: News" -> "USA", "CANADA - SPORTS" -> "CANADA".
 */
fun categoryPrefix(name: String): String {
    val beforeDelimiter = name.split('|', ':', '-', '_').first().trim()
    val candidate = beforeDelimiter.ifEmpty { name.trim() }
    val firstWord = candidate.split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: candidate
    return firstWord.uppercase()
}

/**
 * BasicText/explicit colors rather than material3's Text/FilterChip - shared by both the phone
 * and TV Live TV screens, which have different ambient MaterialThemes (or none, on TV).
 */
@Composable
fun CategoryPrefixFilterRow(
    categories: List<IptvCategoryInfo>,
    selectedPrefix: String?,
    onSelectPrefix: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // distinct() keeps first-occurrence order, which is the playlist's own order - not sorted
    // alphabetically, so the filter row matches how the provider actually ordered categories.
    val prefixes = remember(categories) {
        categories.map(IptvCategoryInfo::name).map(::categoryPrefix).distinct()
    }
    // Not worth a filter row when every category already falls under one group.
    if (prefixes.size <= 1) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            PrefixChip(label = "All", selected = selectedPrefix == null, onClick = { onSelectPrefix(null) })
        }
        items(prefixes) { prefix ->
            PrefixChip(label = prefix, selected = selectedPrefix == prefix, onClick = { onSelectPrefix(prefix) })
        }
    }
}

@Composable
private fun PrefixChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(if (selected) Palette.Accent else Palette.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = if (selected) Palette.Background else Palette.TextMuted, fontSize = 13.sp),
        )
    }
}
