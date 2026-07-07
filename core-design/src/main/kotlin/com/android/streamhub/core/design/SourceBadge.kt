package com.android.streamhub.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.android.streamhub.core.common.domain.SourceType

/**
 * Uses BasicText rather than material3/tv-material3's Text - this needs to render identically
 * inside both theme trees (e.g. a future Master Search result list may mix items from screens
 * built with either), so it can't depend on either theme's Text composable.
 */
@Composable
fun SourceBadge(sourceType: SourceType, modifier: Modifier = Modifier) {
    BasicText(
        text = sourceType.label(),
        modifier = modifier
            .clip(AppShapes.pill)
            .background(sourceType.badgeColor())
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = TextStyle(
            color = Color.White,
            fontFamily = AppFonts.Body,
            fontSize = AppTextStyles.labelSmall.fontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        ),
    )
}
