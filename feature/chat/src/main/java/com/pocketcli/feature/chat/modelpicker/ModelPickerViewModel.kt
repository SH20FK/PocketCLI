package com.pocketcli.feature.chat.modelpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.ModelInfo
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelPickerViewModel @Inject constructor(
    private val connectionManager: ActiveConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelPickerUiState())
    val uiState: StateFlow<ModelPickerUiState> = _uiState.asStateFlow()

    private var allModels: List<ModelInfo> = emptyList()
    private var searchJob: Job? = null

    fun loadModels(initialSelectedId: String? = null) {
        _uiState.update { it.copy(isLoading = true, selectedId = initialSelectedId, error = null) }

        viewModelScope.launch {
            val adapter = connectionManager.getAdapter()
            if (adapter == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Нет активного подключения к серверу",
                        isOfflineCached = true
                    )
                }
                return@launch
            }

            val result = adapter.getModels()
            result.onSuccess { models ->
                allModels = models
                applyFilterAndGrouping(_uiState.value.query)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.localizedMessage ?: "Не удалось загрузить список моделей",
                        isOfflineCached = true
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(120L) // 120ms debounce
            applyFilterAndGrouping(query)
        }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedId = modelId) }
    }

    private fun applyFilterAndGrouping(query: String) {
        val trimmed = query.trim().lowercase()
        val filtered = if (trimmed.isBlank()) {
            allModels
        } else {
            allModels.filter {
                it.name.lowercase().contains(trimmed) ||
                it.modelId.lowercase().contains(trimmed) ||
                it.providerId.lowercase().contains(trimmed)
            }
        }

        val grouped = filtered.groupBy { it.providerId.replaceFirstChar { char -> char.uppercase() } }
        val favs = filtered.take(3)

        _uiState.update {
            it.copy(
                isLoading = false,
                models = filtered,
                favorites = favs,
                groupedModels = grouped
            )
        }
    }
}