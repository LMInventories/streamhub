package com.android.streamhub.feature.iptv.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.streamhub.feature.iptv.data.IptvVodRepository
import com.android.streamhub.feature.iptv.data.VodCategoryInfo
import com.android.streamhub.feature.iptv.data.VodMovieInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodUiState(
    val isSupported: Boolean = true,
    val categories: List<VodCategoryInfo> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val selectedCategory: VodCategoryInfo? = null,
    val movies: List<VodMovieInfo> = emptyList(),
    val isLoadingMovies: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class VodViewModel @Inject constructor(
    private val vodRepository: IptvVodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VodUiState())
    val uiState: StateFlow<VodUiState> = _uiState

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true, errorMessage = null) }
            if (!vodRepository.isSupported()) {
                _uiState.update { it.copy(isSupported = false, isLoadingCategories = false) }
                return@launch
            }
            runCatching { vodRepository.getCategories() }
                .onSuccess { categories -> _uiState.update { it.copy(isSupported = true, categories = categories, isLoadingCategories = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingCategories = false, errorMessage = e.message ?: "Failed to load categories") } }
        }
    }

    fun selectCategory(category: VodCategoryInfo) {
        _uiState.update { it.copy(selectedCategory = category, movies = emptyList(), isLoadingMovies = true) }
        viewModelScope.launch {
            runCatching { vodRepository.getMovies(category.id) }
                .onSuccess { movies -> _uiState.update { it.copy(movies = movies, isLoadingMovies = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingMovies = false, errorMessage = e.message ?: "Failed to load movies") } }
        }
    }

    fun clearCategorySelection() {
        _uiState.update { it.copy(selectedCategory = null, movies = emptyList()) }
    }
}
