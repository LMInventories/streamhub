package com.android.streamhub.feature.iptv.livetv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.streamhub.core.ui.phone.theme.appColorScheme
import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.livetv.cast.CastButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// How long Back has to be held before it's treated as a "long press" shortcut rather than a
// normal press - fires while still held, not on release, so the screen visibly goes fullscreen
// mid-hold rather than waiting for the remote's Back button to come back up.
private const val LONG_PRESS_BACK_FULLSCREEN_DELAY_MS = 2000L

/**
 * Reuses LiveTvScreenPhone's own landscape rendering (LiveTvBrowseContent with isLandscape=true)
 * rather than a second, separately-maintained TV layout - TV is always landscape-shaped, so
 * "phone in landscape" already is the target layout here, just at TV-appropriate sizes. Wraps its
 * own Material3 MaterialTheme since the shared content uses Material3 components (ListItem,
 * DropdownMenu, ...) and the ambient theme from TvNavHost is tv-material3's, not this one - same
 * reasoning as IptvSettingsScreen/JellyfinSettingsScreen/SearchScreen.
 */
@Composable
fun LiveTvScreenTv(
    onFullscreen: (channelId: String) -> Unit,
    onOpenRecordings: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val miniPlayerState by viewModel.miniPlayerUiState.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()

    // Reported by EpgGridPanel as the user scrolls the shared timeline to browse future
    // programmes - null falls back to the channel's real live now/next below, so this only ever
    // overrides while actively scrolled away from "now". Plain local Compose state rather than
    // ViewModel state: uiState.nowProgram/nextProgram are also read by castCurrentChannel() for
    // the Cast session's subtitle, which must stay tied to what's actually playing.
    var previewNowProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var previewNextProgram by remember { mutableStateOf<EpgProgram?>(null) }

    // Resumes the focused channel whenever this screen enters composition (first time, or
    // returning from another tab) and pauses it whenever it leaves - there's no reason to keep a
    // channel the user can't see decoding/buffering in the background. Category/channel browsing
    // (including expanding/collapsing the in-place fullscreen overlay below) happens entirely
    // within this same composable, so it never triggers this - only genuinely navigating away
    // from Live TV does. exitFullscreen() here is a safety net so switching tabs while fullscreen
    // doesn't leave the app's own tab row permanently hidden.
    DisposableEffect(Unit) {
        viewModel.resumeMiniPlayer()
        onDispose {
            viewModel.pauseMiniPlayer()
            viewModel.exitFullscreen()
            // Same "safety net" reasoning as exitFullscreen() above - without this, leaving Live
            // TV entirely (a different tab-row tab) while multiview is open would leave
            // fullscreenOverlayState stuck active and the tab row permanently hidden.
            viewModel.closeMultiview()
        }
    }

    if (uiState.isMultiviewActive) {
        MultiviewOverlay(
            tiles = uiState.multiviewTiles,
            audioFocusChannelId = uiState.multiviewAudioFocusChannelId,
            categories = uiState.categories,
            pickerActiveTab = uiState.multiviewPickerTab,
            recentChannels = recentChannels,
            pickerChannels = uiState.multiviewPickerChannels,
            isLoadingPickerChannels = uiState.isLoadingMultiviewPickerChannels,
            onSelectPickerTab = viewModel::selectMultiviewPickerTab,
            onResetPicker = viewModel::resetMultiviewPicker,
            onTapTile = viewModel::setMultiviewAudioFocus,
            onRemoveTile = viewModel::removeFromMultiview,
            onAddChannel = viewModel::addToMultiview,
            onClose = viewModel::closeMultiview,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // Tracks the physical Back key's down-to-up span with a delayed coroutine (not something
    // BackHandler exposes - it only ever sees a completed, already-classified press) so a genuine
    // long-press while browsing jumps straight to fullscreen mid-hold instead of waiting for
    // release to check elapsed time. KeyDown is deliberately never consumed (returns false) so a
    // normal short press still reaches the regular Back handling untouched; only a KeyUp that
    // arrives after the timer already fired fullscreen is consumed, so that release doesn't also
    // trigger a normal Back navigation on top of the fullscreen it just entered.
    //
    // This modifier lives on the outer Box below - wrapping BOTH the fullscreen and browse
    // branches, not just the browse Column alone - specifically so it stays mounted (and its
    // longPressJob/longPressFired state survives) across the recomposition that swaps in
    // LiveFullscreenOverlay once the timer fires. Attaching it only to the browse Column would
    // mean that Column (and this whole key interceptor) disappears from composition the instant
    // isFullscreen flips true, so the eventual physical KeyUp would fall through uncaught to
    // LiveFullscreenOverlay's own unconditional BackHandler instead - collapsing straight back out
    // of the fullscreen the long-press just opened.
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressFired by remember { mutableStateOf(false) }
    val backKeyScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Back) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (longPressJob == null) {
                            longPressFired = false
                            longPressJob = backKeyScope.launch {
                                delay(LONG_PRESS_BACK_FULLSCREEN_DELAY_MS)
                                if (uiState.focusedChannel != null) {
                                    longPressFired = true
                                    viewModel.enterFullscreen()
                                }
                            }
                        }
                        false
                    }
                    KeyEventType.KeyUp -> {
                        longPressJob?.cancel()
                        longPressJob = null
                        val fired = longPressFired
                        longPressFired = false
                        fired // only consume the release if the timer already went fullscreen
                    }
                    else -> false
                }
            },
    ) {
        val focusedChannel = uiState.focusedChannel
        if (uiState.isFullscreen && focusedChannel != null) {
            LiveFullscreenOverlay(
                exoPlayer = viewModel.miniPlayerController.exoPlayer,
                playerUiState = miniPlayerState,
                channel = focusedChannel,
                nowProgram = uiState.nowProgram,
                nextProgram = uiState.nextProgram,
                recentChannels = recentChannels,
                categories = uiState.categories,
                multiviewPickerTab = uiState.multiviewPickerTab,
                multiviewPickerChannels = uiState.multiviewPickerChannels,
                isLoadingMultiviewPickerChannels = uiState.isLoadingMultiviewPickerChannels,
                onSelectMultiviewPickerTab = viewModel::selectMultiviewPickerTab,
                onResetMultiviewPicker = viewModel::resetMultiviewPicker,
                onSwitchChannel = viewModel::switchFullscreenChannel,
                onPlayPause = viewModel::toggleMiniPlayerPlayback,
                onToggleMute = viewModel::toggleMiniPlayerMute,
                onStartMultiview = viewModel::startMultiviewFromFullscreen,
                onCollapse = viewModel::exitFullscreen,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MaterialTheme(colorScheme = appColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        uiState.errorMessage?.let { error ->
                            Text(text = error, color = Color.Red, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                        }

                        if (!uiState.hasSource) {
                            AddSourcePrompt(modifier = Modifier.weight(1f))
                            return@Column
                        }

                        val previewSizeMultiplier = uiState.previewPlayerSize.multiplier
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().height(180.dp * previewSizeMultiplier)) {
                                MiniPlayerPreview(
                                    exoPlayer = viewModel.miniPlayerController.exoPlayer,
                                    uiState = miniPlayerState,
                                    onTap = viewModel::enterFullscreen,
                                    modifier = Modifier.width(280.dp * previewSizeMultiplier).fillMaxHeight(),
                                )
                                EpgInfoPanel(
                                    channelName = uiState.focusedChannel?.name,
                                    nowProgram = previewNowProgram ?: uiState.nowProgram,
                                    nextProgram = previewNextProgram ?: uiState.nextProgram,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            CastButton(isAvailable = viewModel.isCastAvailable, modifier = Modifier.align(Alignment.TopEnd))
                        }

                        LiveTvBrowseContent(
                            uiState = uiState,
                            // TV is always landscape-shaped, so this always takes the same branch
                            // phone does in landscape (the inline EPG grid) rather than ever
                            // falling back to the plain portrait channel list.
                            isLandscape = true,
                            onSelectCategory = viewModel::selectCategory,
                            onBackToCategories = viewModel::clearCategorySelection,
                            onFocusChannel = viewModel::focusChannel,
                            onPlayFullscreen = onFullscreen,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onScheduleRecording = viewModel::scheduleRecording,
                            onScheduleReminder = viewModel::scheduleReminder,
                            onOpenRecordings = onOpenRecordings,
                            onPreviewProgramChange = { current, next -> previewNowProgram = current; previewNextProgram = next },
                            // Load-bearing, not decorative - see LiveTvScreenPhone's matching comment.
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
