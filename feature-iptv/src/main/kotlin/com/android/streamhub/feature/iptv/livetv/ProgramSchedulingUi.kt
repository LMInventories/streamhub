package com.android.streamhub.feature.iptv.livetv

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.text.BasicText
import com.android.streamhub.core.design.AppShapes
import com.android.streamhub.core.design.Palette
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

// Custom Popup + BasicText/Palette throughout this file rather than Material3's DropdownMenu/
// AlertDialog - EpgGridPanel (which hosts the long-press this triggers from) is shared by phone
// and TV, which have no common ambient MaterialTheme, so a Material3 component here would render
// wrong on TV, same reasoning as the rest of this file's styling.

// A plain Popup + background has no built-in shadow/elevation the way Material3's DropdownMenu
// does, so over busy content (EPG grid rows, video) it could visually blend in rather than read
// as a floating menu. shadow() fixes that regardless of what's behind it. Used by both the
// long-press context menu and the Record/Reminder dialogs that follow it - a deliberately
// different, light off-white/dark-text look from the rest of the app's dark theme, sized to wrap
// its content rather than forcing a wide fixed minimum. internal (not private) so
// MultiviewOverlay's own picker popup can reuse the exact same look rather than duplicating it.
internal fun Modifier.contextMenuSurface(shape: Shape) = this
    .shadow(elevation = 10.dp, shape = shape)
    .background(Palette.ContextMenuSurface, shape)

@Composable
fun ProgramContextMenu(
    onDismiss: () -> Unit,
    onRecord: () -> Unit,
    onReminder: () -> Unit,
) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .contextMenuSurface(AppShapes.small)
                .padding(vertical = 4.dp),
        ) {
            MenuRow("Record", onClick = onRecord)
            MenuRow("Set Reminder", onClick = onReminder)
        }
    }
}

/** Same popup pattern as ProgramContextMenu - shown on long-pressing a channel's label (not a program block) in the EPG grid, where a silent toggle would give no feedback that anything happened. */
@Composable
fun ChannelMultiviewMenu(isStaged: Boolean, onDismiss: () -> Unit, onToggle: () -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .contextMenuSurface(AppShapes.small)
                .padding(vertical = 4.dp),
        ) {
            MenuRow(if (isStaged) "Remove from Multiview" else "Add to Multiview", onClick = onToggle)
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    // No fillMaxWidth() here - that was stretching the row (and with it, the Column/Popup that
    // sizes to its widest child) out to the full available width instead of wrapping to the text,
    // which is what actually made this read as screen-wide despite contextMenuSurface's own
    // "wrap, don't fill" intent above.
    BasicText(
        text = label,
        style = TextStyle(color = Palette.ContextMenuText, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

// Muted variant of the context-menu text color for this off-white surface - there's no shared
// Palette token for it since it's a one-off exception to the app's otherwise dark-only theme (see
// Palette.ContextMenuText's own comment), so it's derived here rather than adding one for a single
// use site.
private val ContextMenuTextMuted = Palette.ContextMenuText.copy(alpha = 0.6f)
private val ContextMenuButtonSurface = Palette.ContextMenuText.copy(alpha = 0.08f)

@Composable
private fun DialogCard(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Same off-white contextMenuSurface as ProgramContextMenu (the long-press menu that opens
    // this) rather than the dark menuSurface these dialogs used before - the two should read as
    // one continuous flow, not a light menu handing off to an unrelated-looking dark dialog.
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 340.dp)
                .contextMenuSurface(AppShapes.medium)
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun DialogTitle(program: EpgProgram, channel: IptvChannelInfo) {
    val use24Hour = rememberUse24HourTime()
    BasicText(text = program.title, style = TextStyle(color = Palette.ContextMenuText, fontSize = 16.sp))
    BasicText(
        text = "${channel.name} · ${dayTimeFormatter(use24Hour).format(program.startAt.atZone(ZoneId.systemDefault()))}",
        style = TextStyle(color = ContextMenuTextMuted, fontSize = 12.sp),
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
    )
}

@Composable
private fun DialogActions(onCancel: () -> Unit, confirmLabel: String, onConfirm: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
        BasicText(
            text = "Cancel",
            style = TextStyle(color = ContextMenuTextMuted, fontSize = 14.sp),
            modifier = Modifier.clickable(onClick = onCancel).padding(12.dp),
        )
        BasicText(
            text = confirmLabel,
            style = TextStyle(color = Palette.Accent, fontSize = 14.sp),
            modifier = Modifier.clickable(onClick = onConfirm).padding(12.dp),
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, onValueChange: (Int) -> Unit, min: Int = -30, max: Int = 30) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        BasicText(text = label, style = TextStyle(color = Palette.ContextMenuText, fontSize = 13.sp), modifier = Modifier.weight(1f))
        StepperButton("−") { onValueChange(max(min, value - 1)) }
        BasicText(
            text = "$value min",
            style = TextStyle(color = Palette.ContextMenuText, fontSize = 13.sp, textAlign = TextAlign.Center),
            modifier = Modifier.width(56.dp).padding(horizontal = 4.dp),
        )
        StepperButton("+") { onValueChange(min(max, value + 1)) }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(ContextMenuButtonSurface, AppShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BasicText(text = symbol, style = TextStyle(color = Palette.ContextMenuText, fontSize = 14.sp))
    }
}

@Composable
fun RecordProgramDialog(
    channel: IptvChannelInfo,
    program: EpgProgram,
    startAdjustMinutes: Int,
    endAdjustMinutes: Int,
    onStartAdjustChange: (Int) -> Unit,
    onEndAdjustChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogCard(onDismiss = onDismiss) {
        DialogTitle(program, channel)
        Stepper("Start earlier by", startAdjustMinutes, onStartAdjustChange)
        Stepper("End later by", endAdjustMinutes, onEndAdjustChange)
        DialogActions(onCancel = onDismiss, confirmLabel = "Schedule Recording", onConfirm = onConfirm)
    }
}

private val REMINDER_LEAD_OPTIONS = listOf(5, 10, 15, 30)

@Composable
fun ReminderProgramDialog(
    channel: IptvChannelInfo,
    program: EpgProgram,
    leadMinutes: Int,
    onLeadMinutesChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Fired once when the dialog appears - notification scheduling itself doesn't depend on this
    // permission (the alarm is set regardless), only whether the notification actually shows
    // when it fires does, so this doesn't block anything on the user's answer.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    DialogCard(onDismiss = onDismiss) {
        DialogTitle(program, channel)
        BasicText(
            text = "Notify me before it starts:",
            style = TextStyle(color = Palette.ContextMenuText, fontSize = 13.sp),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row {
            REMINDER_LEAD_OPTIONS.forEach { minutes ->
                LeadChip(minutes = minutes, selected = leadMinutes == minutes, onClick = { onLeadMinutesChange(minutes) })
            }
        }
        DialogActions(onCancel = onDismiss, confirmLabel = "Set Reminder", onConfirm = onConfirm)
    }
}

@Composable
private fun LeadChip(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .background(if (selected) Palette.Accent else ContextMenuButtonSurface, AppShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BasicText(
            text = "${minutes}m",
            style = TextStyle(color = if (selected) Palette.Background else ContextMenuTextMuted, fontSize = 12.sp),
        )
    }
}
