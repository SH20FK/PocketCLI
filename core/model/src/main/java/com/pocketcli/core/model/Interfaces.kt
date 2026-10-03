package com.pocketcli.core.model

import kotlinx.coroutines.flow.Flow

enum class Capability {
    Streaming,
    Permissions,
    Plan,
    Diff,
    Images
}

interface AgentAdapter {
    val capabilities: Set<Capability>
    suspend fun connect(): Result<Unit>
    fun events(sessionId: String): Flow<AgentEvent>
    suspend fun createSession(title: String): Result<Session>
    suspend fun listSessions(): Result<List<Session>>
    suspend fun sendPrompt(sessionId: String, prompt: Prompt): Result<Unit>
    suspend fun cancel(sessionId: String): Result<Unit>
    suspend fun respondPermission(requestId: String, option: PermissionOption): Result<Unit>
    suspend fun disconnect()
}

interface Transport {
    val baseUrl: String
    val isConnected: Boolean
    suspend fun checkHealth(): Result<HealthInfo>
}

data class HealthInfo(
    val healthy: Boolean,
    val version: String
)
