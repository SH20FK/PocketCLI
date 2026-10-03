package com.pocketcli.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionState {
    IDLE,
    BUSY,
    RETRY,
    ERROR
}

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

@Serializable
enum class ToolStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    ERROR
}

@Serializable
enum class PermissionOption(val value: String) {
    ONCE("once"),
    ALWAYS("always"),
    REJECT("reject")
}

@Serializable
data class PlanEntry(
    val id: String,
    val title: String,
    val isCompleted: Boolean
)

sealed interface AgentEvent {
    data class SessionStatus(val sessionId: String, val state: SessionState) : AgentEvent
    data class MessageStarted(val sessionId: String, val messageId: String, val role: MessageRole) : AgentEvent
    data class TextDelta(val messageId: String, val text: String) : AgentEvent
    data class ReasoningDelta(val messageId: String, val text: String) : AgentEvent
    data class ToolCallUpdate(
        val messageId: String,
        val callId: String,
        val name: String,
        val status: ToolStatus,
        val input: String?,
        val output: String?
    ) : AgentEvent
    data class PermissionRequested(
        val requestId: String,
        val callId: String?,
        val title: String,
        val options: List<PermissionOption> = listOf(PermissionOption.ONCE, PermissionOption.ALWAYS, PermissionOption.REJECT)
    ) : AgentEvent
    data class PlanUpdate(val entries: List<PlanEntry>) : AgentEvent
    data class FileDiff(val path: String, val unifiedDiff: String) : AgentEvent
    data class Error(val message: String, val recoverable: Boolean) : AgentEvent
}
