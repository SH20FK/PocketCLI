package com.pocketcli.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.SessionState
import com.pocketcli.core.ui.components.FileDiffViewer
import com.pocketcli.core.ui.components.PermissionDetailsSheet
import com.pocketcli.core.ui.components.PocketStatusState
import com.pocketcli.core.ui.components.ToolOutputBottomSheet
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.feature.chat.components.ChatComposer
import com.pocketcli.feature.chat.components.ChatTimeline
import com.pocketcli.feature.chat.components.ChatTopBar
import com.pocketcli.feature.chat.modelpicker.ModelPickerRoute

/**
 * ChatScreen - cleanly orchestrating modular components:
 * - ChatTopBar
 * - ChatTimeline
 * - ChatComposer
 * - ModelPickerRoute
 * - Virtualized ToolOutputBottomSheet & PermissionDetailsSheet
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val currentStatusState = when {
        uiState.pendingPermission != null -> PocketStatusState.WAITING_PERMISSION
        uiState.sessionState == SessionState.BUSY -> PocketStatusState.RUNNING
        uiState.errorMessage != null -> PocketStatusState.ERROR
        else -> PocketStatusState.IDLE
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                sessionTitle = uiState.sessionTitle,
                projectName = uiState.workspaceName,
                runtimeName = uiState.runtimeName,
                statusState = currentStatusState,
                onNavigateBack = onNavigateBack,
                onOverflowClick = { viewModel.openModelPicker() }
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
            Column(modifier = Modifier.fillMaxSize()) {
                // Optional error banner
                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    uiState.errorMessage?.let { error ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PocketSpacing.md, vertical = PocketSpacing.xs),
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
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Закрыть",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Diff viewer if opened
                uiState.activeDiffFile?.let { (path, diff) ->
                    Box(modifier = Modifier.padding(PocketSpacing.xs)) {
                        FileDiffViewer(
                            filePath = path,
                            rawUnifiedDiff = diff,
                            onClose = { viewModel.closeDiff() }
                        )
                    }
                }

                // Chat Timeline
                Box(modifier = Modifier.weight(1f)) {
                    ChatTimeline(
                        nodes = uiState.nodes,
                        onOpenToolDetails = { viewModel.openToolDetails(it) },
                        onReplyPermission = { reqId, opt -> viewModel.respondPermission(reqId, opt) },
                        onOpenDiff = { path, diff -> viewModel.openDiff(path, diff) }
                    )
                }

                // Chat Composer
                ChatComposer(
                    state = uiState.composer,
                    onDraftChange = { viewModel.onDraftChange(it) },
                    onAddAttachment = { viewModel.addAttachment(it) },
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    onOpenModelPicker = { viewModel.openModelPicker() },
                    onSend = { viewModel.sendPrompt() },
                    onStop = { viewModel.stop() }
                )
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

        // Model Picker Bottom Sheet (isolated state & ViewModel)
        if (uiState.isModelPickerOpen) {
            ModelPickerRoute(
                selectedModelId = uiState.selectedModel?.modelId,
                agentType = uiState.agentType,
                onSelectModel = { model ->
                    viewModel.selectModel(model)
                },
                onDismiss = { viewModel.dismissModelPicker() }
            )
        }
    }
}