package com.android.streamhub.feature.emby.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.emby.data.EmbyAppSettingsRepository
import com.android.streamhub.feature.emby.data.EmbyBrowseRepository
import com.android.streamhub.feature.emby.data.EmbyHomeSectionKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReorderableSection(val key: String, val title: String)

data class EmbyHomeSectionOrderUiState(
    val isLoading: Boolean = true,
    val sections: List<ReorderableSection> = emptyList(),
)

/** Titles/keys only, not the actual item lists EmbyHomeViewModel's own version of this resolution loads - this screen just needs enough to label and reorder rows, not render them. */
@HiltViewModel
class EmbyHomeSectionOrderViewModel @Inject constructor(
    private val browseRepository: EmbyBrowseRepository,
    private val appSettingsRepository: EmbyAppSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmbyHomeSectionOrderUiState())
    val uiState: StateFlow<EmbyHomeSectionOrderUiState> = _uiState

    init {
        viewModelScope.launch {
            val libraries = browseRepository.getLibraries()
            val defaultSections = listOf(
                ReorderableSection(EmbyHomeSectionKeys.CONTINUE_WATCHING, "Continue Watching"),
                ReorderableSection(EmbyHomeSectionKeys.NEXT_UP, "Next Up"),
                ReorderableSection(EmbyHomeSectionKeys.FAVOURITES, "Favourites"),
            ) + libraries.map { ReorderableSection(EmbyHomeSectionKeys.library(it.id), "Latest in ${it.name}") }
            val byKey = defaultSections.associateBy { it.key }

            val savedOrder = appSettingsRepository.settingsFlow.first().homeSectionOrder
            val ordered = savedOrder.mapNotNull { byKey[it] } + defaultSections.filter { it.key !in savedOrder }
            _uiState.update { it.copy(isLoading = false, sections = ordered) }
        }
    }

    fun moveUp(index: Int) = move(index, index - 1)
    fun moveDown(index: Int) = move(index, index + 1)

    private fun move(from: Int, to: Int) {
        val current = _uiState.value.sections
        if (to !in current.indices) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _uiState.update { it.copy(sections = mutable) }
        viewModelScope.launch {
            appSettingsRepository.update { it.copy(homeSectionOrder = mutable.map { section -> section.key }) }
        }
    }
}
