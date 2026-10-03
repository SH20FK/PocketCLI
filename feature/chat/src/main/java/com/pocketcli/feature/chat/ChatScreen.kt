package com.pocketcli.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.Message
import com.pocketcli.core.model.MessageRole
import com.pocketcli.core.model.ModelInfo
import com.pocketcli.core.model.SessionState
import com.pocketcli.core.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Smart Autoscroll: scroll to bottom on new messages only if user was already at bottom
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= (listState.layoutInfo.totalItemsCount - 2).coerceAtLeast(0)
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text) {
        if (isAtBottom && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(text = "Session Chat", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (uiState.sessionState == SessionState.BUSY) "Agent working..." else "Idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.sessionState == SessionState.BUSY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    FilterChip(
                        selected = uiState.selectedModel != null,
                        onClick = { viewModel.openModelPicker() },
                        label = {
                            Text(
                                text = uiState.selectedModel?.name ?: "Default Model",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Model"
                            )
                        }
                    )
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Optional error banner
            uiState.errorMessage?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Messages Timeline
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onOpenToolDetails = { viewModel.openToolDetails(it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Interactive Permission Request card
                uiState.pendingPermission?.let { perm ->
                    item {
                        PermissionCard(
                            requestId = perm.requestId,
                            title = perm.title,
                            onReply = { reqId, opt -> viewModel.respondPermission(reqId, opt) }
                        )
                    }
                }
            }

            // Composer (Input field + Send/Stop)
            ChatComposer(
                draft = uiState.composerDraft,
                onDraftChange = { viewModel.onDraftChange(it) },
                isBusy = uiState.sessionState == SessionState.BUSY,
                onSend = { viewModel.sendPrompt() },
                onStop = { viewModel.stop() }
            )
        }

        // Virtualized Tool Output Modal Bottom Sheet
        ToolOutputBottomSheet(
            toolCall = uiState.selectedToolForDetails,
            onDismiss = { viewModel.dismissToolDetails() }
        )

        // Model Picker Dialog
        if (uiState.isModelPickerOpen) {
            ModelPickerDialog(
                availableModels = uiState.availableModels,
                selectedModel = uiState.selectedModel,
                onSelectModel = { viewModel.selectModel(it) },
                onDismiss = { viewModel.dismissModelPicker() }
            )
        }
    }
}

@Composable
fun ModelPickerDialog(
    availableModels: List<ModelInfo>,
    selectedModel: ModelInfo?,
    onSelectModel: (ModelInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredModels = remember(availableModels, searchQuery) {
        if (searchQuery.isBlank()) availableModels
        else availableModels.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.modelId.contains(searchQuery, ignoreCase = true) ||
            it.providerId.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Agent Model") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ${availableModels.size} models...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        ListItem(
                            headlineContent = { Text("Default (Server Default)") },
                            supportingContent = { Text("Use server configured default model") },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedModel == null,
                                    onClick = { onSelectModel(null) }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(null) }
                        )
                        HorizontalDivider()
                    }

                    items(filteredModels, key = { "${it.providerId}:${it.modelId}" }) { model ->
                        val isSelected = selectedModel?.providerId == model.providerId && selectedModel.modelId == model.modelId
                        ListItem(
                            headlineContent = { Text(model.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${model.providerId} • ${model.modelId}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectModel(model) }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(model) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MessageBubble(
    message: Message,
    onOpenToolDetails: (com.pocketcli.core.model.ToolCall) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(bgColor)
                .padding(12.dp)
                .widthIn(max = 320.dp)
        ) {
            Column {
                // Optional reasoning block
                message.reasoning?.let { rsn ->
                    if (rsn.isNotBlank()) {
                        ReasoningCard(reasoning = rsn)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Main message text with throttled markdown
                if (message.text.isNotBlank()) {
                    StreamingMarkdownText(markdown = message.text)
                }

                // Tool calls attached to this message
                message.toolCalls.forEach { toolCall ->
                    Spacer(modifier = Modifier.height(6.dp))
                    ToolCallCard(
                        toolCall = toolCall,
                        onOpenDetails = onOpenToolDetails
                    )
                }
            }
        }
    }
}

@Composable
fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isBusy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Ask or instruct agent...") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            if (isBusy) {
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop Agent")
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank()
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send Prompt")
                }
            }
        }
    }
}
