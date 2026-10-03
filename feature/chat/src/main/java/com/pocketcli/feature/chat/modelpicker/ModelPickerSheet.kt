package com.pocketcli.feature.chat.modelpicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.ModelInfo
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.core.ui.theme.ToolSuccessColor

/**
 * ModelPickerSheet according to section 5:
 * - Modal bottom sheet with drag handle
 * - Search field (56dp)
 * - Favorites chips row (if <= 4)
 * - Provider group headers
 * - 64dp model rows with selection checkmark
 * - Offline cached warning if needed
 * - Empty state with clear button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    state: ModelPickerUiState,
    onQueryChange: (String) -> Unit,
    onSelectModel: (ModelInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PocketShapes.container,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PocketSpacing.md)
                .padding(bottom = PocketSpacing.xl)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PocketSpacing.sm)
            ) {
                Text(
                    text = Выбор модели,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state.isOfflineCached) {
                    Text(
                        text = Офлайн-кэш,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search input (56dp)
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(Поиск модели...) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = Поиск,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange(") }) {
 Icon(
 imageVector = Icons.Default.Clear,
 contentDescription = Очистить,
 tint = MaterialTheme.colorScheme.onSurfaceVariant
 )
 }
 }
 },
 shape = PocketShapes.action,
 singleLine = true,
 modifier = Modifier
 .fillMaxWidth()
 .height(56.dp)
 )

 // Optional horizontal favorites row (only if <= 4 favorites and no active search query)
 if (state.query.isBlank() && state.favorites.isNotEmpty() && state.favorites.size <= 4) {
 Spacer(modifier = Modifier.height(PocketSpacing.sm))
 Row(
 horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
 modifier = Modifier
 .fillMaxWidth()
 .horizontalScroll(rememberScrollState())
 ) {
 state.favorites.forEach { model ->
 val isSelected = model.modelId == state.selectedId
 FilterChip(
 selected = isSelected,
 onClick = { onSelectModel(model) },
 label = { Text(model.name, maxLines = 1) },
 shape = PocketShapes.compact
 )
 }
 }
 }

 Spacer(modifier = Modifier.height(PocketSpacing.sm))

 if (state.isLoading) {
 Box(
 modifier = Modifier
 .fillMaxWidth()
 .height(180.dp),
 contentAlignment = Alignment.Center
 ) {
 CircularProgressIndicator()
 }
 } else if (state.models.isEmpty()) {
 // Empty state
 Column(
 modifier = Modifier
 .fillMaxWidth()
 .padding(vertical = PocketSpacing.xl),
 horizontalAlignment = Alignment.CenterHorizontally
 ) {
 Text(
 text = Ничего не найдено,
 style = MaterialTheme.typography.titleMedium,
 color = MaterialTheme.colorScheme.onSurface
 )
 Spacer(modifier = Modifier.height(PocketSpacing.xs))
 TextButton(onClick = { onQueryChange() }) {
 Text(Очистить поиск)
 }
 }
 } else {
 // Grouped models list
 LazyColumn(
 modifier = Modifier
 .fillMaxWidth()
 .heightIn(max = 420.dp)
 ) {
 state.groupedModels.forEach { (provider, modelsInGroup) ->
 item(key = header_) {
 Text(
 text = provider,
 style = MaterialTheme.typography.labelLarge,
 color = MaterialTheme.colorScheme.primary,
 modifier = Modifier.padding(top = PocketSpacing.sm, bottom = PocketSpacing.xxs)
 )
 }

 items(
 items = modelsInGroup,
 key = { : }
 ) { model ->
 val isSelected = model.modelId == state.selectedId
 ModelRowItem(
 model = model,
 isSelected = isSelected,
 onClick = { onSelectModel(model) }
 )
 }
 }
 }
 }
 }
 }
}

@Composable
private fun ModelRowItem(
 model: ModelInfo,
 isSelected: Boolean,
 onClick: () -> Unit
) {
 val containerColor by animateColorAsState(
 targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
 animationSpec = tween(180),
 label = ModelRowContainer
 )

 Surface(
 shape = PocketShapes.compact,
 color = containerColor,
 modifier = Modifier
 .fillMaxWidth()
 .heightIn(min = 64.dp)
 .clip(PocketShapes.compact)
 .clickable(onClick = onClick)
 ) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 modifier = Modifier
 .fillMaxSize()
 .padding(horizontal = PocketSpacing.md, vertical = PocketSpacing.xs)
 ) {
 Column(modifier = Modifier.weight(1f)) {
 Text(
 text = model.name,
 style = MaterialTheme.typography.bodyLarge,
 color = MaterialTheme.colorScheme.onSurface,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis
 )
 Text(
 text =  · ,
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis
 )
 }

 AnimatedVisibility(visible = isSelected) {
 Icon(
 imageVector = Icons.Default.Check,
 contentDescription = Выбрано,
 tint = ToolSuccessColor,
 modifier = Modifier.size(24.dp)
 )
 }
 }
 }
}