package com.pocketcli.feature.chat.modelpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketcli.core.model.ModelInfo

@Composable
fun ModelPickerRoute(
    selectedModelId: String?,
    onSelectModel: (ModelInfo) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ModelPickerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(selectedModelId) {
        viewModel.loadModels(selectedModelId)
    }

    ModelPickerSheet(
        state = state,
        onQueryChange = { viewModel.onQueryChange(it) },
        onSelectModel = { model ->
            viewModel.selectModel(model.modelId)
            onSelectModel(model)
        },
        onDismiss = onDismiss
    )
}