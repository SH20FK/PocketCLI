package com.pocketcli.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.Message
import com.pocketcli.core.model.MessageRole
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
                title = {
                    Column {
                        Text(text = "Session Chat", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (uiState.sessionState == SessionState.BUSY) "Agent working..." else "Idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.sessionState == SessionState.BUSY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    }
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
