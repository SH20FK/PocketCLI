package com.pocketcli.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.Message
import com.pocketcli.core.model.MessageRole
import com.pocketcli.core.model.ModelInfo
import com.pocketcli.core.model.SessionState
import com.pocketcli.core.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Smart Autoscroll detection
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

    val currentStatusState = when {
        uiState.pendingPermission != null -> AgentActivityState.AWAITING_PERMISSION
        uiState.sessionState == SessionState.BUSY -> AgentActivityState.EXECUTING
        else -> AgentActivityState.READY
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = uiState.sessionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = uiState.workspaceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PocketStatusPill(state = currentStatusState)
                        }
                    }
                },
                actions = {
                    FilterChip(
                        selected = uiState.selectedModel != null,
                        onClick = { viewModel.openModelPicker() },
                        label = {
                            Text(
                                text = uiState.selectedModel?.name ?: "Модель",
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
                                contentDescription = "Выбор модели"
                            )
                        }
                    )
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                                    contentDescription = "Закрыть",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Active Diff viewer if opened
                uiState.activeDiffFile?.let { (path, diff) ->
                    Box(modifier = Modifier.padding(8.dp)) {
                        FileDiffViewer(
                            filePath = path,
                            rawUnifiedDiff = diff,
                            onClose = { viewModel.closeDiff() }
                        )
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
                                onReply = { reqId, opt -> viewModel.respondPermission(reqId, opt) },
                                onShowDetails = { viewModel.openPermissionDetails() }
                            )
                        }
                    }
                }

                // Composer with attachments & Send/Stop morph
                PocketChatComposer(
                    draft = uiState.composerDraft,
                    onDraftChange = { viewModel.onDraftChange(it) },
                    attachments = uiState.attachments,
                    onAddAttachment = { viewModel.addAttachment(it) },
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    isBusy = uiState.sessionState == SessionState.BUSY,
                    onSend = { viewModel.sendPrompt() },
                    onStop = { viewModel.stop() }
                )
            }

            // Floating scroll to bottom indicator
            if (!isAtBottom && uiState.messages.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(uiState.messages.size - 1)
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "К новым сообщениям",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Virtualized Tool Output Modal Bottom Sheet
        ToolOutputBottomSheet(
            toolCall = uiState.selectedToolForDetails,
            onDismiss = { viewModel.dismissToolDetails() }
        )

        // Permission Details Bottom Sheet
        if (uiState.showPermissionDetails && uiState.pendingPermission != null) {
            val perm = uiState.pendingPermission!!
            val associatedToolCall = perm.callId?.let { cid ->
                uiState.messages.flatMap { it.toolCalls }.find { it.callId == cid }
            }
            PermissionDetailsSheet(
                requestId = perm.requestId,
                title = perm.title,
                commandOrPayload = associatedToolCall?.inputJson ?: associatedToolCall?.output,
                workingDirectory = if (uiState.workspaceName.isNotBlank() && uiState.workspaceName != "Локально") uiState.workspaceName else null,
                onReply = { reqId, opt -> viewModel.respondPermission(reqId, opt) },
                onDismiss = { viewModel.dismissPermissionDetails() }
            )
        }

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
fun PocketChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<String>,
    onAddAttachment: (String) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    isBusy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Attachment chips row
            if (attachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    attachments.forEachIndexed { index, name ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveAttachment(index) },
                            label = { Text(name, maxLines = 1) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Удалить вложение",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Input pill container
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box {
                        IconButton(onClick = { showAttachMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Вложить файл"
                            )
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Файл из проекта") },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    onAddAttachment("file_${attachments.size + 1}.kt")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Изображение / скриншот") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    onAddAttachment("screenshot_${attachments.size + 1}.png")
                                }
                            )
                        }
                    }

                    TextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        placeholder = { Text("Задайте вопрос или задачу...") },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilledIconButton(
                        onClick = {
                            if (isBusy) onStop() else onSend()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isBusy) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (isBusy) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        PocketAnimatedIcon(
                            state = if (isBusy) IconState.STOP else IconState.SEND,
                            contentDescription = if (isBusy) "Остановить" else "Отправить"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onOpenToolDetails: (com.pocketcli.core.model.ToolCall) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 4.dp
                ),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        )
                    )
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else {
            // Agent Response (Document Style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Reasoning expandable card
                message.reasoning?.takeIf { it.isNotBlank() }?.let { reasoningText ->
                    ReasoningCard(reasoning = reasoningText)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Tool calls
                for (tool in message.toolCalls) {
                    ToolCallCard(
                        toolCall = tool,
                        onOpenDetails = onOpenToolDetails
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Main Markdown text
                if (message.text.isNotBlank()) {
                    MarkdownText(
                        markdown = message.text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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

    PocketDialog(
        onDismissRequest = onDismiss,
        title = "Выбор модели агента",
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск ${availableModels.size} моделей...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    ListItem(
                        headlineContent = { Text("По умолчанию (Серверная)") },
                        supportingContent = { Text("Использовать модель, настроенную на сервере") },
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
    }
}
