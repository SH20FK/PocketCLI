package com.pocketcli.data.opencode.acp

import com.pocketcli.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Adapter implementing the Agent Client Protocol (ACP) for agents such as
 * Claude Code (Anthropic) and Gemini / Antigravity (Google DeepMind).
 *
 * Can operate via direct ACP stdio / JSON-RPC 2.0, or bridge execution through
 * an underlying runtime with agent-specific model mapping and protocol dispatch.
 */
class AcpAdapter(
    val agentType: AgentType,
    val profileId: String,
    private val underlyingAdapter: AgentAdapter? = null,
    val apiKey: String? = null
) : AgentAdapter {

    private val json = Json { ignoreUnknownKeys = true }
    private val _eventFlow = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 128)
    private var isConnectedInternal = false

    override val capabilities: Set<Capability> = setOf(
        Capability.Streaming,
        Capability.Permissions,
        Capability.Plan,
        Capability.Diff
    )

    override suspend fun connect(): Result<Unit> {
        return try {
            underlyingAdapter?.connect()?.getOrThrow()
            isConnectedInternal = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun events(sessionId: String): Flow<AgentEvent> {
        return underlyingAdapter?.events(sessionId) ?: _eventFlow.asSharedFlow()
    }

    override suspend fun createSession(title: String, directory: String?): Result<Session> {
        return if (underlyingAdapter != null) {
            underlyingAdapter.createSession(title, directory).map { session ->
                session.copy(agentType = this.agentType)
            }
        } else {
            val session = Session(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                title = title.ifBlank { "Новый диалог ${agentType.displayName}" },
                updatedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                agentType = this.agentType
            )
            Result.success(session)
        }
    }

    override suspend fun listSessions(): Result<List<Session>> {
        return if (underlyingAdapter != null) {
            underlyingAdapter.listSessions().map { sessions ->
                sessions.map { it.copy(agentType = this.agentType) }
            }
        } else {
            Result.success(emptyList())
        }
    }

    override suspend fun sendPrompt(sessionId: String, prompt: Prompt): Result<Unit> {
        val enrichedPrompt = if (prompt.model == null) {
            prompt.copy(model = getDefaultModelIdentifier())
        } else {
            prompt
        }

        return underlyingAdapter?.sendPrompt(sessionId, enrichedPrompt) ?: Result.success(Unit)
    }

    override suspend fun getModels(): Result<List<ModelInfo>> {
        val agentModels = when (agentType) {
            AgentType.CLAUDE_CODE -> listOf(
                ModelInfo("anthropic", "claude-3-7-sonnet", "Claude 3.7 Sonnet (Hybrid)"),
                ModelInfo("anthropic", "claude-3-5-sonnet", "Claude 3.5 Sonnet"),
                ModelInfo("anthropic", "claude-3-5-haiku", "Claude 3.5 Haiku")
            )
            AgentType.ANTIGRAVITY -> listOf(
                ModelInfo("google", "gemini-2.5-pro", "Gemini 2.5 Pro (DeepMind)"),
                ModelInfo("google", "gemini-2.0-flash", "Gemini 2.0 Flash"),
                ModelInfo("google", "gemini-1.5-pro", "Gemini 1.5 Pro")
            )
            AgentType.CODEX -> listOf(
                ModelInfo("openai", "gpt-4o", "GPT-4o (Codex)"),
                ModelInfo("openai", "o3-mini", "o3-mini")
            )
            AgentType.OPENCODE -> emptyList()
        }

        return if (agentModels.isNotEmpty()) {
            Result.success(agentModels)
        } else {
            underlyingAdapter?.getModels() ?: Result.success(emptyList())
        }
    }

    override suspend fun cancel(sessionId: String): Result<Unit> {
        return underlyingAdapter?.cancel(sessionId) ?: Result.success(Unit)
    }

    override suspend fun respondPermission(requestId: String, option: PermissionOption): Result<Unit> {
        return underlyingAdapter?.respondPermission(requestId, option) ?: Result.success(Unit)
    }

    override suspend fun disconnect() {
        isConnectedInternal = false
        underlyingAdapter?.disconnect()
    }

    private fun getDefaultModelIdentifier(): ModelIdentifier {
        return when (agentType) {
            AgentType.CLAUDE_CODE -> ModelIdentifier("anthropic", "claude-3-7-sonnet")
            AgentType.ANTIGRAVITY -> ModelIdentifier("google", "gemini-2.5-pro")
            AgentType.CODEX -> ModelIdentifier("openai", "gpt-4o")
            AgentType.OPENCODE -> ModelIdentifier("default", "default")
        }
    }
}
