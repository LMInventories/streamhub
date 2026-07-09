package com.android.streamhub.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.streamhub.core.design.Palette

/**
 * Shared "not wired up yet" screen for tabs whose backend integration hasn't landed - real
 * Emby support is future work (see Route.EMBY_HOME_PATTERN), this just keeps the nav shell honest
 * about what's actually available today rather than 404-ing or crashing.
 * BasicText/explicit colors rather than material3.Text since this is reachable from both the
 * phone and TV nav trees, which use different ambient MaterialThemes.
 */
@Composable
fun ComingSoonScreen(
    title: String,
    message: String,
    paddingValues: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .padding(paddingValues)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = Palette.TextPrimary, fontSize = 22.sp, textAlign = TextAlign.Center),
        )
        BasicText(
            text = message,
            style = TextStyle(color = Palette.TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
