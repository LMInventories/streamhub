package com.android.streamhub.feature.emby.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadTracker
import com.android.streamhub.core.tmdb.PersonLookupState
import com.android.streamhub.core.tmdb.TmdbRepository
import com.android.streamhub.feature.emby.data.EmbyAppSettingsRepository
import com.android.streamhub.feature.emby.data.EmbyAudioChoice
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyItemInfo
import com.android.streamhub.feature.emby.data.EmbyPlaybackPreferenceStore
import com.android.streamhub.feature.emby.data.EmbySubtitleChoice
import com.android.streamhub.feature.emby.data.resolveDefaultSubtitleChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmbyItemDetailUiState(
    val isLoading: Boolean = true,
    val item: EmbyItemInfo? = null,
    val errorMessage: String? = null,
    // Always resolved to a definitive value by the time isLoading flips false (see
    // hydrateDefaultSubtitle) - never "untouched". See EmbyPlaybackPreferenceStore's own doc for
    // why this needs to be threaded to a different screen entirely rather than just local UI
    // state. Mirrors JellyfinItemDetailUiState exactly.
    val selectedSubtitleIndex: Int? = null,
    val subtitlesExplicitlyOff: Boolean = false,
    // Same "resolved before any tap" hydration as subtitles above - null only when the item has
    // fewer than 2 options and there's nothing to pick between (see hydrateDefaultAudio/Version).
    val selectedAudioIndex: Int? = null,
    val selectedVersionId: String? = null,
)

/**
 * Loads a single Emby item (movie or episode) for the detail screen, plus everything the detail
 * screen's controls need: favorite/watched toggles, the audio/subtitle/version pickers' hydrated
 * defaults and tap handlers, and download lifecycle. Was deliberately thin through the lean-MVP
 * pass (no pickers, no DownloadTracker, no favorite/watched) since EmbyItemInfo didn't carry any
 * of that data yet - this pass fills it back in to reach parity with
 * JellyfinItemDetailViewModel, which this file mirrors field-for-field/function-for-function.
 */
@HiltViewModel
class EmbyItemDetailViewModel @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
    private val downloadTracker: DownloadTracker,
    private val appSettingsRepository: EmbyAppSettingsRepository,
    private val playbackPreferenceStore: EmbyPlaybackPreferenceStore,
    private val tmdbRepository: TmdbRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(EmbyItemDetailUiState())
    val uiState: StateFlow<EmbyItemDetailUiState> = _uiState

    val downloadInfo: StateFlow<DownloadInfo?> = downloadTracker.downloads
        .map { downloads -> downloads.firstOrNull { it.id == itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _personLookupState = MutableStateFlow<PersonLookupState>(PersonLookupState.Idle)
    val personLookupState: StateFlow<PersonLookupState> = _personLookupState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val item = runCatching { browseRepository.getItem(itemId) }.getOrNull()
            _uiState.update { it.copy(isLoading = false, item = item, errorMessage = if (item == null) "Item not found" else null) }

            if (item != null) {
                hydrateDefaultSubtitle(item)
                hydrateDefaultAudio(item)
                hydrateDefaultVersion(item)
            }
        }
    }

    /**
     * Resolves and commits a definitive default the moment the item loads, before any tap -
     * eliminates the "dropdown shows Off but something else plays" divergence a naive version of
     * this screen would have (the field simply stays null - always displayed as "Off" - until the
     * user acts, while EmbyMediaSource.resolveSubtitlePreference() could still fall through to an
     * app-wide language preference the screen never reflected). An existing store entry (this item
     * was already visited this session) wins; otherwise defers to resolveDefaultSubtitleChoice, the
     * same function EmbyMediaSource.resolveSubtitlePreference falls back to for the
     * Downloads-bypass path, so the two can never resolve differently for the same item. Writes to
     * both uiState (what the dropdown shows) and the store (what resolvePlayback() reads) together,
     * so the two can never disagree by the time Play is pressed from this screen.
     */
    private suspend fun hydrateDefaultSubtitle(item: EmbyItemInfo) {
        val existing = playbackPreferenceStore.get(itemId)?.subtitle
        val resolved = existing ?: run {
            val preferredLanguage = appSettingsRepository.settingsFlow.first().preferredSubtitleLanguage
            item.resolveDefaultSubtitleChoice(preferredLanguage)
        }
        playbackPreferenceStore.setSubtitle(itemId, resolved)
        _uiState.update {
            when (resolved) {
                is EmbySubtitleChoice.Off -> it.copy(selectedSubtitleIndex = null, subtitlesExplicitlyOff = true)
                is EmbySubtitleChoice.Track -> it.copy(selectedSubtitleIndex = resolved.index, subtitlesExplicitlyOff = false)
            }
        }
    }

    /** Same hydrate-before-any-tap shape as hydrateDefaultSubtitle above - existing choice, else the stream the server itself flags as default, else the first one. No "Off" concept for audio - a picker only makes sense once there's more than one track (see the detail screens' own size > 1 gating), so a single-track item is left unhydrated. */
    private fun hydrateDefaultAudio(item: EmbyItemInfo) {
        if (item.audioTracks.size <= 1) return
        val existing = playbackPreferenceStore.get(itemId)?.audio
        val resolved = existing ?: (item.audioTracks.firstOrNull { it.isDefault } ?: item.audioTracks.first())
            .let { EmbyAudioChoice(it.index, it.language) }
        playbackPreferenceStore.setAudio(itemId, resolved)
        _uiState.update { it.copy(selectedAudioIndex = resolved.index) }
    }

    /** Same shape again - existing choice, else the first version (server's own listed order). Only meaningful once there's more than one version to pick between; see the detail screens' own size > 1 gating. */
    private fun hydrateDefaultVersion(item: EmbyItemInfo) {
        if (item.videoVersions.size <= 1) return
        val resolved = playbackPreferenceStore.get(itemId)?.mediaSourceId ?: item.videoVersions.first().id
        playbackPreferenceStore.setMediaSourceId(itemId, resolved)
        _uiState.update { it.copy(selectedVersionId = resolved) }
    }

    fun toggleFavorite() {
        val item = _uiState.value.item ?: return
        // Optimistic - flips immediately so the button feels responsive, then reconciles with
        // whatever the server actually confirms (or reverts silently on failure).
        val optimistic = !item.isFavorite
        _uiState.update { it.copy(item = it.item?.copy(isFavorite = optimistic)) }
        viewModelScope.launch {
            val confirmed = browseRepository.toggleFavorite(item.id, item.isFavorite)
            if (confirmed != null) {
                _uiState.update { it.copy(item = it.item?.copy(isFavorite = confirmed)) }
            } else {
                _uiState.update { it.copy(item = it.item?.copy(isFavorite = item.isFavorite)) }
            }
        }
    }

    fun toggleWatched() {
        val item = _uiState.value.item ?: return
        // Same optimistic-then-reconcile shape as toggleFavorite above.
        val optimistic = !item.isPlayed
        _uiState.update { it.copy(item = it.item?.copy(isPlayed = optimistic)) }
        viewModelScope.launch {
            val confirmed = browseRepository.toggleWatched(item.id, item.isPlayed)
            if (confirmed != null) {
                _uiState.update { it.copy(item = it.item?.copy(isPlayed = confirmed)) }
            } else {
                _uiState.update { it.copy(item = it.item?.copy(isPlayed = item.isPlayed)) }
            }
        }
    }

    // EmbyItemInfo doesn't carry a resolved stream URL up front - getStreamUrl() involves its own
    // PlaybackInfo round-trip, so it's only ever fetched lazily, right when actually needed
    // (playback, or here).
    fun startDownload() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            val streamUrl = runCatching { browseRepository.getStreamUrl(item.id) }.getOrNull() ?: return@launch
            downloadTracker.startDownload(itemId, SourceType.EMBY, item.name, item.primaryImageUrl, streamUrl)
        }
    }

    /** null selects "Off" (hard-disables subtitles at playback); a track's own index selects that track by its language. */
    fun selectSubtitle(index: Int?) {
        if (index == null) {
            _uiState.update { it.copy(selectedSubtitleIndex = null, subtitlesExplicitlyOff = true) }
            playbackPreferenceStore.setSubtitle(itemId, EmbySubtitleChoice.Off)
            return
        }
        val track = _uiState.value.item?.subtitleTracks?.firstOrNull { it.index == index } ?: return
        _uiState.update { it.copy(selectedSubtitleIndex = index, subtitlesExplicitlyOff = false) }
        playbackPreferenceStore.setSubtitle(itemId, EmbySubtitleChoice.Track(track.index, track.language))
    }

    fun selectAudioTrack(index: Int) {
        val track = _uiState.value.item?.audioTracks?.firstOrNull { it.index == index } ?: return
        _uiState.update { it.copy(selectedAudioIndex = index) }
        playbackPreferenceStore.setAudio(itemId, EmbyAudioChoice(track.index, track.language))
    }

    fun selectVideoVersion(mediaSourceId: String) {
        _uiState.update { it.copy(selectedVersionId = mediaSourceId) }
        playbackPreferenceStore.setMediaSourceId(itemId, mediaSourceId)
    }

    fun pauseDownload() = downloadTracker.pauseDownload(itemId)
    fun resumeDownload() = downloadTracker.resumeDownload(itemId)
    fun removeDownload() = downloadTracker.removeDownload(itemId)

    /** See JellyfinItemDetailViewModel.onPersonClick's matching doc - identical shape, both sources share TmdbRepository unchanged. */
    fun onPersonClick(name: String) {
        _personLookupState.value = PersonLookupState.Loading
        viewModelScope.launch {
            val person = tmdbRepository.findPerson(name)
            _personLookupState.value = person?.let { PersonLookupState.Found(it.id) } ?: PersonLookupState.NotFound
        }
    }

    fun consumePersonLookup() {
        _personLookupState.value = PersonLookupState.Idle
    }
}
