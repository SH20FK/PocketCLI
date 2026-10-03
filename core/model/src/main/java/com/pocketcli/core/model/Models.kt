package com.pocketcli.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val callId: String,
    val messageId: String,
    val name: String,
    val status: ToolStatus,
    val inputJson: String?,
    val output: String?,
    val isTruncated: Boolean = false
)

@Serializable
data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val text: String,
    val reasoning: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class Session(
    val id: String,
    val profileId: String,
    val title: String,
    val updatedAt: Long,
    val createdAt: Long,
    val workspaceId: String? = null
)

@Serializable
enum class WorkspaceSourceType {
    CREATED,
    CLONED,
    IMPORTED
}

@Serializable
data class Workspace(
    val id: String,
    val profileId: String,
    val displayName: String,
    val localPath: String,
    val sourceType: WorkspaceSourceType = WorkspaceSourceType.CREATED,
    val remoteUrl: String? = null,
    val defaultBranch: String = "main",
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false
)

@Serializable
data class WorkspaceGitStatus(
    val branch: String? = null,
    val isGitRepo: Boolean = false,
    val isDirty: Boolean = false,
    val fileCount: Int = 0
)

data class WorkspaceWithDetails(
    val workspace: Workspace,
    val gitStatus: WorkspaceGitStatus = WorkspaceGitStatus(),
    val sessionCount: Int = 0
)

@Serializable
data class ModelIdentifier(
    val providerId: String,
    val modelId: String
)

@Serializable
data class ModelInfo(
    val providerId: String,
    val modelId: String,
    val name: String
)

@Serializable
data class Prompt(
    val text: String,
    val model: ModelIdentifier? = null,
    val mode: String? = null
)

@Serializable
data class ConnectionProfile(
    val id: String,
    val name: String,
    val url: String,
    val username: String = "opencode",
    val allowCleartextHttp: Boolean = false,
    val lastConnectedAt: Long = 0L
)
