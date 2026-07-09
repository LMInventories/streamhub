package com.android.streamhub.feature.iptv.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.IptvAppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class IptvTimeFormatViewModel @Inject constructor(
    repository: IptvAppSettingsRepository,
) : ViewModel() {
    val use24Hour: StateFlow<Boolean> = repository.settingsFlow
        .map { it.use24HourTime }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}

/**
 * A tiny dedicated ViewModel rather than threading a use24Hour: Boolean parameter through every
 * screen/composable between LiveTvViewModel and wherever a time actually gets formatted (EPG
 * info panel, EPG grid, record/reminder dialogs, Recordings screen) - those call sites span
 * several files/ViewModels that don't otherwise share state, so reading the setting locally where
 * it's needed is far less invasive than plumbing it through every one of those signatures.
 */
@Composable
fun rememberUse24HourTime(): Boolean {
    val viewModel: IptvTimeFormatViewModel = hiltViewModel()
    val use24Hour by viewModel.use24Hour.collectAsStateWithLifecycle()
    return use24Hour
}

fun timeFormatter(use24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a")

fun dayTimeFormatter(use24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24Hour) "EEE HH:mm" else "EEE h:mm a")

fun dateTimeFormatter(use24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24Hour) "d MMM, HH:mm" else "d MMM, h:mm a")
