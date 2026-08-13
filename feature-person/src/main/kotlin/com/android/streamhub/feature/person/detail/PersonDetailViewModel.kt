package com.android.streamhub.feature.person.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.library.EmbyLibraryFinder
import com.android.streamhub.core.common.library.JellyfinLibraryFinder
import com.android.streamhub.core.common.library.LibraryTitleFinder
import com.android.streamhub.core.tmdb.TmdbFilmographyEntry
import com.android.streamhub.core.tmdb.TmdbPersonDetail
import com.android.streamhub.core.tmdb.TmdbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LibraryMatchState {
    data object Pending : LibraryMatchState()
    data class Matched(val itemId: String) : LibraryMatchState()
    data object NotInLibrary : LibraryMatchState()
}

data class FilmographyItem(
    val tmdbId: Int,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val isSeries: Boolean,
    val matchState: LibraryMatchState,
)

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val person: TmdbPersonDetail? = null,
    val movies: List<FilmographyItem> = emptyList(),
    val tvShows: List<FilmographyItem> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Only the most recent MATCH_LOOKUP_LIMIT credits per carousel are eagerly matched against the
 * user's own library, in small concurrent batches - a prolific actor's filmography can run past
 * 100 credits, and firing that many concurrent search requests at the user's own Jellyfin/Emby
 * server on every actor-page visit would be wasteful and could look like abuse. A deliberate
 * scope/perf cap, not a hard technical limit - trivially raised later if needed.
 */
private const val MATCH_LOOKUP_LIMIT = 24
private const val MATCH_LOOKUP_BATCH_SIZE = 4

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val tmdbRepository: TmdbRepository,
    @JellyfinLibraryFinder private val jellyfinFinder: LibraryTitleFinder,
    @EmbyLibraryFinder private val embyFinder: LibraryTitleFinder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tmdbPersonId: Int = checkNotNull(savedStateHandle["tmdbPersonId"])
    private val sourceType: SourceType = SourceType.valueOf(checkNotNull(savedStateHandle["sourceType"]))
    private val libraryFinder: LibraryTitleFinder = if (sourceType == SourceType.JELLYFIN) jellyfinFinder else embyFinder

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val (person, filmography) = coroutineScope {
                val personDeferred = async { tmdbRepository.getPersonDetail(tmdbPersonId) }
                val filmographyDeferred = async { tmdbRepository.getFilmography(tmdbPersonId) }
                personDeferred.await() to filmographyDeferred.await()
            }

            if (person == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Actor not found") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    person = person,
                    movies = filmography.movies.map { entry -> entry.toPendingItem(isSeries = false) },
                    tvShows = filmography.tvShows.map { entry -> entry.toPendingItem(isSeries = true) },
                )
            }

            launch { resolveLibraryMatches(isSeries = false) }
            launch { resolveLibraryMatches(isSeries = true) }
        }
    }

    private suspend fun resolveLibraryMatches(isSeries: Boolean) {
        val items = (if (isSeries) _uiState.value.tvShows else _uiState.value.movies).take(MATCH_LOOKUP_LIMIT)
        items.chunked(MATCH_LOOKUP_BATCH_SIZE).forEach { batch ->
            coroutineScope {
                batch.map { item ->
                    async {
                        val match = runCatching { libraryFinder.findInLibrary(item.title, item.year, isSeries) }.getOrNull()
                        val newState = if (match != null) LibraryMatchState.Matched(match.itemId) else LibraryMatchState.NotInLibrary
                        updateMatchState(tmdbId = item.tmdbId, isSeries = isSeries, matchState = newState)
                    }
                }.forEach { it.await() }
            }
        }
    }

    private fun updateMatchState(tmdbId: Int, isSeries: Boolean, matchState: LibraryMatchState) {
        _uiState.update { state ->
            if (isSeries) {
                state.copy(tvShows = state.tvShows.map { if (it.tmdbId == tmdbId) it.copy(matchState = matchState) else it })
            } else {
                state.copy(movies = state.movies.map { if (it.tmdbId == tmdbId) it.copy(matchState = matchState) else it })
            }
        }
    }

    private fun TmdbFilmographyEntry.toPendingItem(isSeries: Boolean): FilmographyItem = FilmographyItem(
        tmdbId = tmdbId,
        title = title,
        posterUrl = posterUrl,
        year = year,
        isSeries = isSeries,
        matchState = LibraryMatchState.Pending,
    )
}
