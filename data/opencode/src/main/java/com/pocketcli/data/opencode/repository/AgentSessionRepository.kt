package com.pocketcli.data.opencode.repository

import com.pocketcli.core.model.*
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.api.OpenCodeApiClient
import com.pocketcli.data.local.db.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class StreamingMessageState(
    val id: String,
    val sessionId: String,
    val profileId: String,
    val role: MessageRole = MessageRole.ASSISTANT,
    val text: String = "",
    val reasoning: String? = null,
    val activeToolCalls: Map<String, ToolCall> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMessage(): Message = Message(
        id = id,
        sessionId = sessionId,
        role = role,
        text = text,
        reasoning = reasoning,
        toolCalls = activeToolCalls.values.toList(),
        timestamp = timestamp
    )

    fun toEntity(): MessageEntity = MessageEntity(
        profileId = profileId,
        sessionId = sessionId,
        messageId = id,
        role = role.name,
        text = text,
        reasoning = reasoning,
        timestamp = timestamp
    )
}

@Singleton
class AgentSessionRepository @Inject constructor(
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightMessages = MutableStateFlow<Map<String, StreamingMessageState>>(emptyMap())

    fun getActiveSessionIds(): StateFlow<Set<String>> {
        return inFlightMessages
            .map { it.keys }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getActiveSessionInfo(): Flow<ActiveSessionInfo?> {
        return inFlightMessages.transformLatest { map ->
            val firstInFlight = map.values.firstOrNull()
            if (firstInFlight == null) {
                emit(null)
                return@transformLatest
            }
            val session = database.sessionDao().getSessionBySessionId(firstInFlight.sessionId)
            val wsId = session?.workspaceId
            val projectName = if (wsId != null) {
                database.workspaceDao().getById(wsId)?.displayName ?: session.title
            } else {
                session?.title?.takeIf { it.isNotBlank() } ?: "Сессия"
            }
            val runningTool = firstInFlight.activeToolCalls.values.findLast { it.status == ToolStatus.RUNNING }
                ?: firstInFlight.activeToolCalls.values.lastOrNull()
            val action = when {
                runningTool != null -> "Выполняет ${runningTool.name}"
                firstInFlight.reasoning?.isNotEmpty() == true && firstInFlight.text.isEmpty() -> "Размышляет..."
                firstInFlight.text.isNotEmpty() -> "Печатает ответ..."
                else -> "Обработка запроса..."
            }
            while (true) {
                val elapsed = (System.currentTimeMillis() - firstInFlight.timestamp) / 1000
                emit(
                    ActiveSessionInfo(
                        sessionId = firstInFlight.sessionId,
                        profileId = firstInFlight.profileId,
                        projectName = projectName,
                        currentAction = action,
                        elapsedSeconds = elapsed.coerceAtLeast(0)
                    )
                )
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun getSessions(profileId: String): Flow<List<Session>> {
        return database.sessionDao().getSessions(profileId).map { entities ->
            entities.map {
                Session(
                    id = it.sessionId,
                    profileId = it.profileId,
                    title = it.title,
                    updatedAt = it.updatedAt,
                    createdAt = it.createdAt,
                    workspaceId = it.workspaceId,
                    agentType = AgentType.fromId(it.agentType)
                )
            }
        }
    }

    suspend fun saveSession(session: Session) {
        database.sessionDao().upsert(
            SessionEntity(
                profileId = session.profileId,
                sessionId = session.id,
                title = session.title,
                updatedAt = session.updatedAt,
                createdAt = session.createdAt,
                workspaceId = session.workspaceId,
                agentType = session.agentType.id
            )
        )
    }

    suspend fun deleteSession(profileId: String, sessionId: String) {
        database.sessionDao().delete(profileId, sessionId)
    }

    suspend fun getSession(profileId: String, sessionId: String): Session? {
        return database.sessionDao().getSession(profileId, sessionId)?.let {
            Session(
                id = it.sessionId,
                profileId = it.profileId,
                title = it.title,
                updatedAt = it.updatedAt,
                createdAt = it.createdAt,
                workspaceId = it.workspaceId,
                agentType = AgentType.fromId(it.agentType)
            )
        }
    }

    suspend fun getProfileIdForSession(sessionId: String): String? {
        return database.sessionDao().getSessionBySessionId(sessionId)?.profileId
    }

    /**
     * Hybrid stream: combines persisted Room messages with in-flight streaming tail.
     */
    fun getMessages(profileId: String, sessionId: String): Flow<List<Message>> {
        val dbMessagesFlow = database.messageDao().getMessages(profileId, sessionId)
        val dbToolCallsFlow = database.toolCallDao().getToolCallsForSession(profileId, sessionId)

        val combinedDbFlow = combine(dbMessagesFlow, dbToolCallsFlow) { messages, toolCalls ->
            val toolCallsByMsgId = toolCalls.groupBy { it.messageId }
            messages.map { msg ->
                val calls = toolCallsByMsgId[msg.messageId]?.map { tc ->
                    val status = runCatching { ToolStatus.valueOf(tc.status) }.getOrDefault(ToolStatus.COMPLETED)
                    ToolCall(
                        callId = tc.callId,
                        messageId = tc.messageId,
                        name = tc.name,
                        status = status,
                        inputJson = tc.inputJson,
                        output = tc.output,
                        isTruncated = tc.isTruncated
                    )
                } ?: emptyList()

                val role = runCatching { MessageRole.valueOf(msg.role) }.getOrDefault(MessageRole.ASSISTANT)
                Message(
                    id = msg.messageId,
                    sessionId = msg.sessionId,
                    role = role,
                    text = msg.text,
                    reasoning = msg.reasoning,
                    toolCalls = calls,
                    timestamp = msg.timestamp
                )
            }
        }

        return combine(combinedDbFlow, inFlightMessages) { dbMessages, inFlightMap ->
            val inFlight = inFlightMap[sessionId]
            val list = if (inFlight != null) {
                // If message is in-flight and not yet in DB, append it to the tail
                if (dbMessages.none { it.id == inFlight.id }) {
                    dbMessages + inFlight.toMessage()
                } else {
                    // Update the in-progress message with streaming delta
                    dbMessages.map { if (it.id == inFlight.id) inFlight.toMessage() else it }
                }
            } else {
                dbMessages
            }
            list.distinctBy { it.id }
        }
    }

    suspend fun recordUserMessage(profileId: String, sessionId: String, text: String, messageId: String = "msg_${System.currentTimeMillis()}") {
        val entity = MessageEntity(
            profileId = profileId,
            sessionId = sessionId,
            messageId = messageId,
            role = MessageRole.USER.name,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        database.messageDao().upsert(entity)
    }

    fun handleAgentEvent(profileId: String, sessionId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.MessageStarted -> {
                inFlightMessages.update { map ->
                    val updated = map.toMutableMap()
                    updated[sessionId] = StreamingMessageState(
                        id = event.messageId,
                        sessionId = sessionId,
                        profileId = profileId,
                        role = event.role
                    )
                    updated
                }
            }

            is AgentEvent.TextDelta -> {
                inFlightMessages.update { map ->
                    val current = map[sessionId]
                    val updated = map.toMutableMap()
                    if (current != null && current.id == event.messageId) {
                        updated[sessionId] = current.copy(text = current.text + event.text)
                    } else {
                        updated[sessionId] = StreamingMessageState(
                            id = event.messageId,
                            sessionId = sessionId,
                            profileId = profileId,
                            role = MessageRole.ASSISTANT,
                            text = event.text
                        )
                    }
                    updated
                }
            }

            is AgentEvent.ReasoningDelta -> {
                inFlightMessages.update { map ->
                    val current = map[sessionId]
                    val updated = map.toMutableMap()
                    if (current != null && current.id == event.messageId) {
                        val newReasoning = (current.reasoning ?: "") + event.text
                        updated[sessionId] = current.copy(reasoning = newReasoning)
                    } else {
                        updated[sessionId] = StreamingMessageState(
                            id = event.messageId,
                            sessionId = sessionId,
                            profileId = profileId,
                            role = MessageRole.ASSISTANT,
                            reasoning = event.text
                        )
                    }
                    updated
                }
            }

            is AgentEvent.ToolCallUpdate -> {
                inFlightMessages.update { map ->
                    val current = map[sessionId]
                    val updatedTools = (current?.activeToolCalls ?: emptyMap()).toMutableMap()
                    val (sanitizedOutput, isTruncated) = DbSanitizer.sanitizeOutput(event.output)
                    updatedTools[event.callId] = ToolCall(
                        callId = event.callId,
                        messageId = event.messageId,
                        name = event.name,
                        status = event.status,
                        inputJson = event.input,
                        output = sanitizedOutput,
                        isTruncated = isTruncated
                    )
                    val updated = map.toMutableMap()
                    if (current != null) {
                        updated[sessionId] = current.copy(activeToolCalls = updatedTools)
                    } else {
                        updated[sessionId] = StreamingMessageState(
                            id = event.messageId,
                            sessionId = sessionId,
                            profileId = profileId,
                            role = MessageRole.ASSISTANT,
                            activeToolCalls = updatedTools
                        )
                    }
                    updated
                }

                // If tool call is finished, flush it to Room
                if (event.status == ToolStatus.COMPLETED || event.status == ToolStatus.ERROR) {
                    scope.launch {
                        val (sanitizedOutput, isTruncated) = DbSanitizer.sanitizeOutput(event.output)
                        database.toolCallDao().upsert(
                            ToolCallEntity(
                                profileId = profileId,
                                sessionId = sessionId,
                                messageId = event.messageId,
                                callId = event.callId,
                                name = event.name,
                                status = event.status.name,
                                inputJson = event.input,
                                output = sanitizedOutput,
                                isTruncated = isTruncated
                            )
                        )
                    }
                }
            }

            is AgentEvent.SessionStatus -> {
                if (event.state == SessionState.IDLE) {
                    flushInFlightToDb(sessionId)
                }
            }

            else -> Unit
        }
    }

    fun flushInFlightToDb(sessionId: String) {
        val inFlight = inFlightMessages.value[sessionId] ?: return
        scope.launch {
            // Write completed message entity
            database.messageDao().upsert(inFlight.toEntity())

            // Write completed tool calls
            val toolEntities = inFlight.activeToolCalls.values.map { tool ->
                val (sanitized, isTrunc) = DbSanitizer.sanitizeOutput(tool.output)
                ToolCallEntity(
                    profileId = inFlight.profileId,
                    sessionId = sessionId,
                    messageId = inFlight.id,
                    callId = tool.callId,
                    name = tool.name,
                    status = tool.status.name,
                    inputJson = tool.inputJson,
                    output = sanitized,
                    isTruncated = isTrunc
                )
            }
            if (toolEntities.isNotEmpty()) {
                database.toolCallDao().upsertAll(toolEntities)
            }

            // Clear in-flight state
            inFlightMessages.update { map ->
                val updated = map.toMutableMap()
                updated.remove(sessionId)
                updated
            }
        }
    }

    /**
     * Reconcile: Query remote server for latest messages and sync with Room.
     */
    suspend fun reconcile(apiClient: OpenCodeApiClient, profileId: String, sessionId: String) {
        val remoteMessages = apiClient.getMessages(sessionId).getOrNull() ?: return
        val existingMessages = database.messageDao().getMessagesList(profileId, sessionId)
        val optimisticUserMessages = existingMessages.filter { it.role == MessageRole.USER.name }.toMutableList()

        for (item in remoteMessages) {
            val role = if (item.info.role.equals("user", ignoreCase = true)) MessageRole.USER else MessageRole.ASSISTANT
            var fullText = ""
            var reasoning: String? = null
            val toolCalls = mutableListOf<ToolCallEntity>()

            for (part in item.parts) {
                when (part.type) {
                    "text" -> {
                        fullText += (part.text ?: "")
                    }
                    "reasoning" -> {
                        reasoning = (reasoning ?: "") + (part.text ?: "")
                    }
                    "tool" -> {
                        val status = when (part.state?.status?.lowercase()) {
                            "completed" -> ToolStatus.COMPLETED
                            "error" -> ToolStatus.ERROR
                            "running" -> ToolStatus.RUNNING
                            else -> ToolStatus.PENDING
                        }
                        val (sanitizedOutput, isTruncated) = DbSanitizer.sanitizeOutput(part.state?.output ?: part.state?.error)
                        toolCalls.add(
                            ToolCallEntity(
                                profileId = profileId,
                                sessionId = sessionId,
                                messageId = item.info.id,
                                callId = part.callID ?: part.id,
                                name = part.tool ?: "unknown_tool",
                                status = status.name,
                                inputJson = null,
                                output = sanitizedOutput,
                                isTruncated = isTruncated
                            )
                        )
                    }
                }
            }

            // Deduplicate optimistic user message
            if (role == MessageRole.USER) {
                val matchingOptimistic = optimisticUserMessages.firstOrNull {
                    it.messageId != item.info.id && it.text == fullText
                }
                if (matchingOptimistic != null) {
                    database.messageDao().delete(profileId, sessionId, matchingOptimistic.messageId)
                    optimisticUserMessages.remove(matchingOptimistic)
                }
            }

            val messageEntity = MessageEntity(
                profileId = profileId,
                sessionId = sessionId,
                messageId = item.info.id,
                role = role.name,
                text = fullText,
                reasoning = reasoning,
                timestamp = item.info.time?.created ?: System.currentTimeMillis()
            )
            database.messageDao().upsert(messageEntity)
            if (toolCalls.isNotEmpty()) {
                database.toolCallDao().upsertAll(toolCalls)
            }
        }
    }
}
