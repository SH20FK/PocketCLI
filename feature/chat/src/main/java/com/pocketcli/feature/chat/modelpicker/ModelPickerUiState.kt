package com.pocketcli.feature.chat.modelpicker

import androidx.compose.runtime.Immutable
import com.pocketcli.core.model.ModelInfo

@Immutable
data class ModelPickerUiState(
    val query: String = "",
 val isLoading: Boolean = false,
 val models: List<ModelInfo> = emptyList(),
 val favorites: List<ModelInfo> = emptyList(),
 val groupedModels: Map<String, List<ModelInfo>> = emptyMap(),
 val selectedId: String? = null,
 val error: String? = null,
 val isOfflineCached: Boolean = false
)