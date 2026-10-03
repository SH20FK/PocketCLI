package com.pocketcli.feature.chat.model

import androidx.compose.runtime.Immutable
import com.pocketcli.core.model.AgentEvent
import com.pocketcli.core.model.Message
import com.pocketcli.core.model.ToolCall
import com.pocketcli.core.model.ToolStatus

enum class AgentPhase {
    IDLE,
    THINKING,
    STREAMING_TEXT,
    EXECUTING_TOOL,
    AWAITING_PERMISSION,
    COMPLETED,
    ERROR
}

@Immutable
data class ToolSummary(
    val callId: String,
    val name: String,
    val status: ToolStatus = ToolStatus.RUNNING,
    val summary: String = ""
)

@Immutable
data class StreamingTailUi(
 val messageId: String,
 val visibleText: String,
 val phase: AgentPhase,
 val currentTool: ToolSummary? = null,
 val startedAt: Long = System.currentTimeMillis()
)

/**
 * ChatNode represents a unit in the chat timeline:
 * - User message (bubble on right)
 * - Assistant message (full width, markdown)
 * - Tool item (collapsed 48-56dp row)
 * - Permission item (tertiaryContainer, interactive)
 * - Diff summary item
 * - Streaming tail (living indicator at bottom during stream)
 */
sealed interface ChatNode {
 val id: String

 data class UserNode(
 override val id: String,
 val text: String,
 val timestamp: Long
 ) : ChatNode

 data class AssistantNode(
 override val id: String,
 val message: Message,
 val isStreaming: Boolean = false
 ) : ChatNode

 data class ToolNode(
 override val id: String,
 val toolCall: ToolCall,
 val durationMillis: Long? = null
 ) : ChatNode

 data class PermissionNode(
 override val id: String,
 val request: AgentEvent.PermissionRequested
 ) : ChatNode

 data class DiffNode(
 override val id: String,
 val filePath: String,
 val diffContent: String,
 val additions: Int = 0,
 val deletions: Int = 0
 ) : ChatNode

 data class StreamingTailNode(
 override val id: String,
 val tail: StreamingTailUi
 ) : ChatNode
}