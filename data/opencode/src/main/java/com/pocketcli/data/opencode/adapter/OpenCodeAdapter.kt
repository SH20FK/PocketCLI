package com.pocketcli.data.opencode.adapter

import com.pocketcli.core.model.*
import com.pocketcli.data.opencode.api.*
import com.pocketcli.data.opencode.sse.OpenCodeSseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.*

class OpenCodeAdapter(
    val apiClient: OpenCodeApiClient,
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
        val partTypes = java.util.concurrent.ConcurrentHashMap<String, String>()

        return sseClient.events()
            .mapNotNull { event ->
                val payload = event.payload
                val obj = runCatching { payload.properties.jsonObject }.getOrNull() ?: return@mapNotNull null

                when (payload.type) {
                    "session.status" -> {
                        val sid = obj["sessionID"]?.jsonPrimitive?.contentOrNull
                        val statusType = obj["status"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                        if (sid == sessionId && statusType != null) {
                            val state = when (statusType.lowercase()) {
                                "busy" -> SessionState.BUSY
                                "retry" -> SessionState.RETRY
                                "error" -> SessionState.ERROR
                                else -> SessionState.IDLE
                            }
                            AgentEvent.SessionStatus(sessionId, state)
                        } else null
                    }

                    "session.idle" -> {
                        val sid = obj["sessionID"]?.jsonPrimitive?.contentOrNull
                        if (sid == null || sid == sessionId) {
                            AgentEvent.SessionStatus(sessionId, SessionState.IDLE)
                        } else null
                    }

                    "message.updated" -> {
                        val info = obj["info"]?.jsonObject
                        val sid = info?.get("sessionID")?.jsonPrimitive?.contentOrNull
                        val msgId = info?.get("id")?.jsonPrimitive?.contentOrNull
                        val roleStr = info?.get("role")?.jsonPrimitive?.contentOrNull

                        if (sid == sessionId && msgId != null) {
                            val role = if (roleStr.equals("user", ignoreCase = true)) {
                                MessageRole.USER
                            } else {
                                MessageRole.ASSISTANT
                            }
                            if (role == MessageRole.USER) {
                                null
                            } else {
                                AgentEvent.MessageStarted(sessionId, msgId, role)
                            }
                        } else null
                    }

                    "message.part.delta" -> {
                        val sid = obj["sessionID"]?.jsonPrimitive?.contentOrNull
                        val msgId = obj["messageID"]?.jsonPrimitive?.contentOrNull
                        val partId = obj["partID"]?.jsonPrimitive?.contentOrNull
                        val field = obj["field"]?.jsonPrimitive?.contentOrNull
                        val delta = obj["delta"]?.jsonPrimitive?.contentOrNull

                        if (sid == sessionId && msgId != null && delta != null) {
                            val partType = if (partId != null) partTypes[partId] else null
                            val isReasoning = field.equals("reasoning", ignoreCase = true) || partType == "reasoning"
                            if (isReasoning) {
                                AgentEvent.ReasoningDelta(msgId, delta)
                            } else {
                                AgentEvent.TextDelta(msgId, delta)
                            }
                        } else null
                    }

                    "message.part.updated" -> {
                        val part = obj["part"]?.jsonObject
                        val sid = part?.get("sessionID")?.jsonPrimitive?.contentOrNull
                        val partId = part?.get("id")?.jsonPrimitive?.contentOrNull
                        val type = part?.get("type")?.jsonPrimitive?.contentOrNull

                        if (partId != null && type != null) {
                            partTypes[partId] = type
                        }

                        if (sid == sessionId && type == "tool") {
                            val msgId = part?.get("messageID")?.jsonPrimitive?.contentOrNull ?: ""
                            val callId = part?.get("callID")?.jsonPrimitive?.contentOrNull
                                ?: part?.get("id")?.jsonPrimitive?.contentOrNull ?: ""
                            val toolName = part?.get("tool")?.jsonPrimitive?.contentOrNull ?: "unknown_tool"
                            val state = part?.get("state")?.jsonObject
                            val statusStr = state?.get("status")?.jsonPrimitive?.contentOrNull ?: "completed"
                            val status = when (statusStr.lowercase()) {
                                "running" -> ToolStatus.RUNNING
                                "completed" -> ToolStatus.COMPLETED
                                "error" -> ToolStatus.ERROR
                                else -> ToolStatus.PENDING
                            }
                            val input = state?.get("input")?.toString()
                            val output = state?.get("output")?.jsonPrimitive?.contentOrNull
                                ?: state?.get("error")?.jsonPrimitive?.contentOrNull

                            AgentEvent.ToolCallUpdate(
                                messageId = msgId,
                                callId = callId,
                                name = toolName,
                                status = status,
                                input = input,
                                output = output
                            )
                        } else if (sid == sessionId && type == "reasoning") {
                            val msgId = part?.get("messageID")?.jsonPrimitive?.contentOrNull ?: ""
                            val text = part?.get("text")?.jsonPrimitive?.contentOrNull
                            if (!text.isNullOrEmpty()) {
                                AgentEvent.ReasoningDelta(msgId, text)
                            } else null
                        } else null
                    }

                    "message.part.removed" -> {
                        val partId = obj["partID"]?.jsonPrimitive?.contentOrNull
                        if (partId != null) partTypes.remove(partId)
                        null
                    }

                    "todo.updated" -> {
                        val sid = obj["sessionID"]?.jsonPrimitive?.contentOrNull
                        val todosArray = obj["todos"]?.jsonArray

                        if ((sid == null || sid == sessionId) && todosArray != null) {
                            val todoItems = todosArray.mapNotNull { item ->
                                val itemObj = item as? JsonObject ?: return@mapNotNull null
                                val content = itemObj["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                                val status = itemObj["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                                val priority = itemObj["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
                                TodoItem(
                                    content = content,
                                    status = status,
                                    priority = priority
                                )
                            }
                            AgentEvent.TodoUpdate(todoItems)
                        } else null
                    }

                    "permission.asked" -> {
                        val sid = obj["sessionID"]?.jsonPrimitive?.contentOrNull
                        val reqId = obj["id"]?.jsonPrimitive?.contentOrNull
                        val permission = obj["permission"]?.jsonPrimitive?.contentOrNull ?: "command"
                        val toolObj = obj["tool"]?.jsonObject
                        val callId = toolObj?.get("callID")?.jsonPrimitive?.contentOrNull

                        if (sid == sessionId && reqId != null) {
                            AgentEvent.PermissionRequested(
                                requestId = reqId,
                                callId = callId,
                                title = "Permission request: $permission",
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

    override suspend fun getModels(): Result<List<ModelInfo>> {
        return apiClient.getProviders().map { providersDto ->
            val connectedSet = providersDto.connected.toSet()
            val list = mutableListOf<ModelInfo>()

            val targetProviders = if (connectedSet.isNotEmpty()) {
                providersDto.all.filter { connectedSet.contains(it.id) }
            } else {
                providersDto.all
            }

            for (provider in targetProviders) {
                for ((modelId, modelDto) in provider.models) {
                    val name = modelDto.name ?: modelId
                    list.add(
                        ModelInfo(
                            providerId = provider.id,
                            modelId = modelId,
                            name = name
                        )
                    )
                }
            }

            list.sortedWith(
                compareBy<ModelInfo> { if (it.providerId == "opencode") 0 else 1 }
                    .thenBy { it.name }
            )
        }
    }

    override suspend fun createSession(title: String, directory: String?): Result<Session> {
        return apiClient.createSession(title, directory).map { dto ->
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
        return apiClient.sendMessage(sessionId, prompt.text, modelInput).map { }
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
