package com.pocketcli.data.opencode.adapter

import com.pocketcli.core.model.*
import com.pocketcli.data.opencode.api.*
import com.pocketcli.data.opencode.sse.OpenCodeSseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class OpenCodeAdapter(
    private val apiClient: OpenCodeApiClient,
    private val sseClient: OpenCodeSseClient,
    private val profileId: String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
) : AgentAdapter {

    override val capabilities: Set<Capability> = setOf(
        Capability.Streaming,
        Capability.Permissions,
        Capability.Diff
    )

    override suspend fun connect(): Result<Unit> {
        return apiClient.getHealth().map { }
    }

    override fun events(sessionId: String): Flow<AgentEvent> {
        return sseClient.events()
            .mapNotNull { event ->
                val payload = event.payload
                when (payload.type) {
                    "session.status" -> {
                        val props = runCatching {
                            json.decodeFromJsonElement<OpenCodeSessionStatusProperties>(payload.properties)
                        }.getOrNull() ?: return@mapNotNull null

                        if (props.sessionID == sessionId) {
                            val state = when (props.status.type.lowercase()) {
                                "busy" -> SessionState.BUSY
                                "retry" -> SessionState.RETRY
                                "error" -> SessionState.ERROR
                                else -> SessionState.IDLE
                            }
                            AgentEvent.SessionStatus(sessionId, state)
                        } else null
                    }

                    "session.idle" -> {
                        AgentEvent.SessionStatus(sessionId, SessionState.IDLE)
                    }

                    "message.updated" -> {
                        val props = runCatching {
                            json.decodeFromJsonElement<OpenCodeMessageUpdatedProperties>(payload.properties)
                        }.getOrNull() ?: return@mapNotNull null

                        if (props.info.sessionID == sessionId) {
                            val role = if (props.info.role.equals("user", ignoreCase = true)) {
                                MessageRole.USER
                            } else {
                                MessageRole.ASSISTANT
                            }
                            AgentEvent.MessageStarted(sessionId, props.info.id, role)
                        } else null
                    }

                    "message.part.delta" -> {
                        val props = runCatching {
                            json.decodeFromJsonElement<OpenCodePartDeltaProperties>(payload.properties)
                        }.getOrNull() ?: return@mapNotNull null

                        if (props.sessionID == sessionId) {
                            if (props.field.equals("reasoning", ignoreCase = true)) {
                                AgentEvent.ReasoningDelta(props.messageID, props.delta)
                            } else {
                                AgentEvent.TextDelta(props.messageID, props.delta)
                            }
                        } else null
                    }

                    "message.part.updated" -> {
                        val props = runCatching {
                            json.decodeFromJsonElement<OpenCodePartUpdatedProperties>(payload.properties)
                        }.getOrNull() ?: return@mapNotNull null

                        val part = props.part
                        if (part.sessionID == sessionId && part.type == "tool") {
                            val status = when (part.state?.status?.lowercase()) {
                                "running" -> ToolStatus.RUNNING
                                "completed" -> ToolStatus.COMPLETED
                                "error" -> ToolStatus.ERROR
                                else -> ToolStatus.PENDING
                            }
                            val inputStr = part.state?.input?.toString()
                            AgentEvent.ToolCallUpdate(
                                messageId = part.messageID ?: "",
                                callId = part.callID ?: part.id,
                                name = part.tool ?: "unknown_tool",
                                status = status,
                                input = inputStr,
                                output = part.state?.output ?: part.state?.error
                            )
                        } else null
                    }

                    "permission.asked" -> {
                        val props = runCatching {
                            json.decodeFromJsonElement<OpenCodePermissionAskedProperties>(payload.properties)
                        }.getOrNull() ?: return@mapNotNull null

                        if (props.sessionID == sessionId) {
                            AgentEvent.PermissionRequested(
                                requestId = props.id,
                                callId = props.tool?.callID,
                                title = "Permission request: ${props.permission}",
                                options = listOf(
                                    PermissionOption.ONCE,
                                    PermissionOption.ALWAYS,
                                    PermissionOption.REJECT
                                )
                            )
                        } else null
                    }

                    else -> null
                }
            }
    }

    override suspend fun createSession(title: String): Result<Session> {
        return apiClient.createSession(title).map { dto ->
            Session(
                id = dto.id,
                profileId = profileId,
                title = dto.title,
                updatedAt = dto.time.updated,
                createdAt = dto.time.created
            )
        }
    }

    override suspend fun listSessions(): Result<List<Session>> {
        return apiClient.listSessions().map { list ->
            list.map { dto ->
                Session(
                    id = dto.id,
                    profileId = profileId,
                    title = dto.title,
                    updatedAt = dto.time.updated,
                    createdAt = dto.time.created
                )
            }
        }
    }

    override suspend fun sendPrompt(sessionId: String, prompt: Prompt): Result<Unit> {
        val modelInput = prompt.model?.let {
            OpenCodeModelInput(providerID = it.providerId, modelID = it.modelId)
        }
        return apiClient.sendMessage(sessionId, prompt.text, modelInput)
    }

    override suspend fun cancel(sessionId: String): Result<Unit> {
        return apiClient.abortSession(sessionId).map { }
    }

    override suspend fun respondPermission(requestId: String, option: PermissionOption): Result<Unit> {
        return apiClient.replyPermission(requestId, option.value)
    }

    override suspend fun disconnect() {
        // Disconnect handled by lifecycle scope
    }
}
