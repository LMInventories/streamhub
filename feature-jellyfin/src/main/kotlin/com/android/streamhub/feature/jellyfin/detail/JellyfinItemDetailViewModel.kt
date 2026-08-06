package com.android.streamhub.feature.jellyfin.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.player.download.DownloadInfo
import com.android.streamhub.core.player.download.DownloadTracker
import com.android.streamhub.feature.jellyfin.data.JellyfinAppSettingsRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinAudioChoice
import com.android.streamhub.feature.jellyfin.data.JellyfinBrowseRepository
import com.android.streamhub.feature.jellyfin.data.JellyfinItemInfo
import com.android.streamhub.feature.jellyfin.data.JellyfinPlaybackPreferenceStore
import com.android.streamhub.feature.jellyfin.data.JellyfinSubtitleChoice
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

data class JellyfinItemDetailUiState(
    val isLoading: Boolean = true,
    val item: JellyfinItemInfo? = null,
    val errorMessage: String? = null,
    // Always resolved to a definitive value by the time isLoading flips false (see
    // hydrateDefaultSubtitle) - never "untouched" the way this used to default to null, which is
    // the whole point of this fix. See JellyfinPlaybackPreferenceStore's own doc for why this needs
    // to be threaded to a different screen entirely rather than just local UI state.
    val selectedSubtitleIndex: Int? = null,
    val subtitlesExplicitlyOff: Boolean = false,
    // Same "resolved before any tap" hydration as subtitles above - null only when the item has
    // fewer than 2 options and there's nothing to pick between (see hydrateDefaultAudio/Version).
    val selectedAudioIndex: Int? = null,
    val selectedVersionId: String? = null,
    // Only populated for episodes (excludes the current item) - "More from Season X" row.
    val seasonEpisodes: List<JellyfinItemInfo> = emptyList(),
)

@HiltViewModel
class JellyfinItemDetailViewModel @Inject constructor(
    private val browseRepository: JellyfinBrowseRepository,
    private val downloadTracker: DownloadTracker,
    private val appSettingsRepository: JellyfinAppSettingsRepository,
    private val playbackPreferenceStore: JellyfinPlaybackPreferenceStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _uiState = MutableStateFlow(JellyfinItemDetailUiState())
    val uiState: StateFlow<JellyfinItemDetailUiState> = _uiState

    val downloadInfo: StateFlow<DownloadInfo?> = downloadTracker.downloads
        .map { downloads -> downloads.firstOrNull { it.id == itemId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

            val seriesId = item?.seriesId
            val seasonId = item?.seasonId
            if (item != null && seriesId != null && seasonId != null) {
                val episodes = runCatching { browseRepository.getEpisodes(seriesId, seasonId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(seasonEpisodes = episodes.filter { episode -> episode.id != itemId }) }
            }
        }
    }

    /**
     * Resolves and commits a definitive default the moment the item loads, before any tap -
     * eliminates the "dropdown shows Off but something else plays" divergence a previous version of
     * this screen had (the field simply stayed null - always displayed as "Off" - until the user
     * acted, while resolveSubtitlePreference() could still fall through to an app-wide language
     * preference the screen never reflected). An existing store entry (this item was already
     * visited this session) wins; otherwise falls back to the app-wide preferred-subtitle-language
     * setting matched against this item's own tracks; otherwise explicit Off. Writes to both
     * uiState (what the dropdown shows) and the store (what resolvePlayback() reads) together, so
     * the two can never disagree by the time Play is pressed from this screen.
     */
    private suspend fun hydrateDefaultSubtitle(item: JellyfinItemInfo) {
        val existing = playbackPreferenceStore.get(itemId)?.subtitle
        val resolved = existing ?: run {
            val preferredLanguage = appSettingsRepository.settingsFlow.first().preferredSubtitleLanguage
            val match = preferredLanguage?.let { lang -> item.subtitleTracks.firstOrNull { it.language == lang } }
            if (match != null) JellyfinSubtitleChoice.Track(match.language) else JellyfinSubtitleChoice.Off
        }
        playbackPreferenceStore.setSubtitle(itemId, resolved)
        _uiState.update {
            when (resolved) {
                is JellyfinSubtitleChoice.Off -> it.copy(selectedSubtitleIndex = null, subtitlesExplicitlyOff = true)
                is JellyfinSubtitleChoice.Track -> it.copy(
                    selectedSubtitleIndex = item.subtitleTracks.firstOrNull { track -> track.language == resolved.language }?.index,
                    subtitlesExplicitlyOff = false,
                )
            }
        }
    }

    /** Same hydrate-before-any-tap shape as hydrateDefaultSubtitle above - existing choice, else the stream the server itself flags as default, else the first one. No "Off" concept for audio - a picker only makes sense once there's more than one track (see the detail screens' own size > 1 gating), so a single-track item is left unhydrated. */
    private fun hydrateDefaultAudio(item: JellyfinItemInfo) {
        if (item.audioTracks.size <= 1) return
        val existing = playbackPreferenceStore.get(itemId)?.audio
        val resolved = existing ?: (item.audioTracks.firstOrNull { it.isDefault } ?: item.audioTracks.first())
            .let { JellyfinAudioChoice(it.index, it.language) }
        playbackPreferenceStore.setAudio(itemId, resolved)
        _uiState.update { it.copy(selectedAudioIndex = resolved.index) }
    }

    /** Same shape again - existing choice, else the first version (server's own listed order). Only meaningful once there's more than one version to pick between; see the detail screens' own size > 1 gating. */
    private fun hydrateDefaultVersion(item: JellyfinItemInfo) {
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

    // JellyfinItemInfo (unlike VOD's VodDetailInfo) doesn't carry a resolved stream URL up
    // front - getStreamUrl() involves its own PlaybackInfo/transcode-negotiation round-trip, so
    // it's only ever fetched lazily, right when actually needed (playback, or here).
    fun startDownload() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            val streamUrl = runCatching { browseRepository.getStreamUrl(item.id) }.getOrNull() ?: return@launch
            downloadTracker.startDownload(itemId, SourceType.JELLYFIN, item.name, item.primaryImageUrl, streamUrl)
        }
    }

    /** null selects "Off" (hard-disables subtitles at playback); a track's own index selects that track by its language. */
    fun selectSubtitle(index: Int?) {
        if (index == null) {
            _uiState.update { it.copy(selectedSubtitleIndex = null, subtitlesExplicitlyOff = true) }
            playbackPreferenceStore.setSubtitle(itemId, JellyfinSubtitleChoice.Off)
            return
        }
        val track = _uiState.value.item?.subtitleTracks?.firstOrNull { it.index == index } ?: return
        _uiState.update { it.copy(selectedSubtitleIndex = index, subtitlesExplicitlyOff = false) }
        playbackPreferenceStore.setSubtitle(itemId, JellyfinSubtitleChoice.Track(track.language))
    }

    fun selectAudioTrack(index: Int) {
        val track = _uiState.value.item?.audioTracks?.firstOrNull { it.index == index } ?: return
        _uiState.update { it.copy(selectedAudioIndex = index) }
        playbackPreferenceStore.setAudio(itemId, JellyfinAudioChoice(track.index, track.language))
    }

    fun selectVideoVersion(mediaSourceId: String) {
        _uiState.update { it.copy(selectedVersionId = mediaSourceId) }
        playbackPreferenceStore.setMediaSourceId(itemId, mediaSourceId)
    }

    fun pauseDownload() = downloadTracker.pauseDownload(itemId)
    fun resumeDownload() = downloadTracker.resumeDownload(itemId)
    fun removeDownload() = downloadTracker.removeDownload(itemId)
}
