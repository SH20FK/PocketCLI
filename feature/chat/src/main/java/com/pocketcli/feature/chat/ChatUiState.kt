package com.pocketcli.feature.chat

import androidx.compose.runtime.Immutable
import com.pocketcli.core.model.*
import com.pocketcli.feature.chat.model.ChatNode
import com.pocketcli.feature.chat.model.ComposerState
import com.pocketcli.feature.chat.model.StreamingTailUi

@Immutable
data class ChatUiState(
    val sessionId: String = "",
    val profileId: String = "",
    val sessionTitle: String = "Чат сессии",
    val workspaceName: String = "Локально",
    val runtimeName: String = "OpenCode",
    val messages: List<Message> = emptyList(),
    val nodes: List<ChatNode> = emptyList(),
    val composer: ComposerState = ComposerState(),
    val sessionState: SessionState = SessionState.IDLE,
    val pendingPermission: AgentEvent.PermissionRequested? = null,
    val showPermissionDetails: Boolean = false,
    val selectedToolForDetails: ToolCall? = null,
    val activeDiffFile: Pair<String, String>? = null,
    val composerDraft: String = "",
    val attachments: List<String> = emptyList(),
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: ModelInfo? = null,
    val isModelPickerOpen: Boolean = false,
    val errorMessage: String? = null,
    val streamingTail: StreamingTailUi? = null
)
