package com.pocketcli.data.opencode.antigravity

import com.pocketcli.core.model.*
import com.pocketcli.core.security.AntigravityAuthManager
import com.pocketcli.core.security.ResilientDns
import com.pocketcli.data.local.db.AppDatabase
import com.pocketcli.data.local.db.SessionEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val thought: Boolean? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject? = null
)

@Serializable
data class GeminiFunctionResponse(
    val name: String,
    val response: JsonObject
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>? = null,
    val googleSearch: JsonObject? = null
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: GeminiParametersSchema? = null
)

@Serializable
data class GeminiParametersSchema(
    val type: String = "OBJECT",
    val properties: Map<String, GeminiPropertySchema>? = null,
    val required: List<String>? = null
)

@Serializable
data class GeminiPropertySchema(
    val type: String = "STRING",
    val description: String? = null
)

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = 0.7
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val index: Int? = null,
    val groundingMetadata: GeminiGroundingMetadata? = null
)

@Serializable
data class GeminiGroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val searchEntryPoint: GeminiSearchEntryPoint? = null,
    val groundingChunks: List<GeminiGroundingChunk>? = null
)

@Serializable
data class GeminiSearchEntryPoint(
    val renderedContent: String? = null
)

@Serializable
data class GeminiGroundingChunk(
    val web: GeminiWebChunk? = null
)

@Serializable
data class GeminiWebChunk(
    val uri: String? = null,
    val title: String? = null
)

@Serializable
data class AntigravityBoQRequest(
    val project: String = "aicode-consumers",
    val model: String,
    val userAgent: String = "antigravity",
    val requestType: String = "agent",
    val request: GeminiGenerateRequest
)

@Serializable
data class GeminiInnerResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiStreamChunk(
    val candidates: List<GeminiCandidate>? = null,
    val response: GeminiInnerResponse? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

private data class EndpointCandidate(
    val url: String,
    val jsonBody: String,
    val headers: Map<String, String>,
    val description: String
)

private data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String
)

/**
 * Native adapter for Google Antigravity / Gemini CLI.
 *
 * Connects directly to Google Generative Language API via streaming SSE,
 * authenticated via Google OAuth Bearer tokens managed by AntigravityAuthManager
 * or Gemini API Keys.
 */
class AntigravityAdapter(
    val authManager: AntigravityAuthManager,
    val database: AppDatabase? = null,
    val profileId: String = "antigravity",
    val underlyingAdapter: AgentAdapter? = null,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(ResilientDns)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : AgentAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _eventFlow = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val activeJobs = ConcurrentHashMap<String, Job>()

    override val capabilities: Set<Capability> = setOf(
        Capability.Streaming,
        Capability.Permissions,
        Capability.Plan,
        Capability.Diff
    )

    private val standardTools = listOf(
        GeminiTool(
            functionDeclarations = listOf(
                GeminiFunctionDeclaration(
                    name = "web_search",
                    description = "Perform a live web search to retrieve current information, articles, documentation, or news from the internet.",
                    parameters = GeminiParametersSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "query" to GeminiPropertySchema(
                                type = "STRING",
                                description = "The search query string"
                            )
                        ),
                        required = listOf("query")
                    )
                ),
                GeminiFunctionDeclaration(
                    name = "bash",
                    description = "Execute a shell command in the project directory.",
                    parameters = GeminiParametersSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "command" to GeminiPropertySchema(
                                type = "STRING",
                                description = "The bash/shell command line to run"
                            )
                        ),
                        required = listOf("command")
                    )
                ),
                GeminiFunctionDeclaration(
                    name = "read_file",
                    description = "Read the text content of a file in the workspace.",
                    parameters = GeminiParametersSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "path" to GeminiPropertySchema(
                                type = "STRING",
                                description = "Relative or absolute file path to read"
                            )
                        ),
                        required = listOf("path")
                    )
                ),
                GeminiFunctionDeclaration(
                    name = "write_file",
                    description = "Create or overwrite a file with the given content in the workspace.",
                    parameters = GeminiParametersSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "path" to GeminiPropertySchema(
                                type = "STRING",
                                description = "File path to write"
                            ),
                            "content" to GeminiPropertySchema(
                                type = "STRING",
                                description = "Content to write into the file"
                            )
                        ),
                        required = listOf("path", "content")
                    )
                ),
                GeminiFunctionDeclaration(
                    name = "list_dir",
                    description = "List files and subdirectories in a workspace directory.",
                    parameters = GeminiParametersSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "path" to GeminiPropertySchema(
                                type = "STRING",
                                description = "Directory path to list (defaults to current project directory if omitted)"
                            )
                        )
                    )
                )
            )
        )
    )

    companion object {
        private val logHistory = java.util.concurrent.ConcurrentLinkedDeque<String>()
        private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
        val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

        fun log(msg: String) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
            val formatted = "[$timestamp] [Antigravity] $msg"
            try {
                android.util.Log.i("Antigravity", formatted)
            } catch (_: Throwable) {
                println(formatted)
            }
            logHistory.add(formatted)
            while (logHistory.size > 1000) {
                logHistory.poll()
            }
            _logsFlow.value = logHistory.toList()
        }

        fun clearLogs() {
            logHistory.clear()
            _logsFlow.value = emptyList()
        }
    }

    override suspend fun connect(): Result<Unit> {
        log("connect() checking authorization...")
        val tokenRes = authManager.getValidAccessToken()
        return if (tokenRes.isSuccess) {
            val token = tokenRes.getOrNull().orEmpty()
            val tokenKind = if (token.startsWith("AIza")) "API_KEY" else "OAUTH_BEARER"
            log("connect() success with $tokenKind (${token.take(6)}...)")
            Result.success(Unit)
        } else {
            val err = tokenRes.exceptionOrNull()?.message ?: "Google Antigravity не авторизован"
            log("connect() failed: $err")
            Result.failure(tokenRes.exceptionOrNull() ?: IllegalStateException(err))
        }
    }

    override fun events(sessionId: String): Flow<AgentEvent> {
        return _eventFlow.asSharedFlow()
    }

    override suspend fun createSession(title: String, directory: String?): Result<Session> {
        val sessionId = "antigravity_${UUID.randomUUID().toString().take(8)}"
        log("createSession: id=$sessionId title='$title' directory=$directory")
        val session = Session(
            id = sessionId,
            profileId = profileId,
            title = title.ifBlank { "Диалог с Antigravity" },
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            workspaceId = directory,
            agentType = AgentType.ANTIGRAVITY
        )
        database?.sessionDao()?.upsert(
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
        return Result.success(session)
    }

    override suspend fun listSessions(): Result<List<Session>> {
        val entities = database?.sessionDao()?.getSessions(profileId)?.firstOrNull() ?: emptyList()
        val sessions = entities.map {
            Session(
                id = it.sessionId,
                profileId = it.profileId,
                title = it.title,
                updatedAt = it.updatedAt,
                createdAt = it.createdAt,
                workspaceId = it.workspaceId,
                agentType = AgentType.fromId(it.agentType)
            )
        }.filter { it.agentType == AgentType.ANTIGRAVITY }
        return Result.success(sessions)
    }

    override suspend fun sendPrompt(sessionId: String, prompt: Prompt): Result<Unit> {
        val model = prompt.model?.modelId
            ?: authManager.state.value.selectedModel.ifEmpty { AntigravityAuthManager.DEFAULT_MODEL }

        log("sendPrompt: session=$sessionId model=$model promptLen=${prompt.text.length}")

        val activeJob = activeJobs[sessionId]
        if (activeJob?.isActive == true) {
            log("Canceling existing active job for session $sessionId")
            activeJob.cancel()
        }

        val job = scope.launch {
            val assistantMsgId = "asst_${UUID.randomUUID()}"
            try {
                _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.BUSY))
                _eventFlow.emit(AgentEvent.MessageStarted(sessionId, assistantMsgId, MessageRole.ASSISTANT))

                // Get valid auth token
                val tokenResult = authManager.getValidAccessToken()
                if (tokenResult.isFailure) {
                    val errorMsg = tokenResult.exceptionOrNull()?.message ?: "Ошибка авторизации Google Antigravity"
                    log("sendPrompt auth failed: $errorMsg")
                    _eventFlow.emit(AgentEvent.TextDelta(assistantMsgId, "❌ $errorMsg\n\n*Авторизуйтесь в Настройках -> Gemini / Antigravity.*"))
                    _eventFlow.emit(AgentEvent.Error(errorMsg, recoverable = true))
                    _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.ERROR))
                    return@launch
                }
                val token = tokenResult.getOrThrow()
                val isApiKey = token.startsWith("AIza")
                log("Auth token valid: kind=${if (isApiKey) "API_KEY" else "OAUTH_BEARER"} (${token.take(6)}...)")

                // Resolve workspace directory
                val sessionEntity = database?.sessionDao()?.getSession(profileId, sessionId)
                val workspaceDir = sessionEntity?.workspaceId

                // Load prior conversation messages for multi-turn context
                val conversationContents = mutableListOf<GeminiContent>()
                val priorMessages = database?.messageDao()?.getMessagesList(profileId, sessionId).orEmpty()
                for (msg in priorMessages) {
                    val role = if (msg.role == MessageRole.USER.name) "user" else "model"
                    if (msg.text.isNotBlank()) {
                        conversationContents.add(
                            GeminiContent(
                                role = role,
                                parts = listOf(GeminiPart(text = msg.text))
                            )
                        )
                    }
                }
                // Append current user prompt only if not already present at the end of history
                val lastMsg = conversationContents.lastOrNull()
                val alreadyAppended = lastMsg?.role == "user" && lastMsg.parts.firstOrNull()?.text?.trim() == prompt.text.trim()
                if (!alreadyAppended && prompt.text.isNotBlank()) {
                    conversationContents.add(
                        GeminiContent(
                            role = "user",
                            parts = listOf(GeminiPart(text = prompt.text))
                        )
                    )
                }

                var currentTurn = 0
                val maxTurns = 8
                var lastStreamSuccess = false
                val attemptSummary = StringBuilder()

                while (currentTurn < maxTurns && isActive) {
                    currentTurn++
                    val turnToolCalls = mutableListOf<GeminiFunctionCall>()
                    val turnTextAccumulated = StringBuilder()
                    var chunkCount = 0
                    var turnSuccess = false

                    val requestPayload = GeminiGenerateRequest(
                        contents = conversationContents,
                        systemInstruction = GeminiContent(
                            role = "system",
                            parts = listOf(
                                GeminiPart(
                                    text = "You are Antigravity, an advanced AI coding assistant developed by Google DeepMind. You write clean, robust, production-ready code, follow best engineering practices, and provide structured reasoning when thinking through complex problems. You have access to tools for live web search and workspace files/commands. Use web_search whenever the user asks for up-to-date information, documentation, news, or external references. Use bash, read_file, write_file, and list_dir to inspect, run, or edit files in the workspace."
                                )
                            )
                        ),
                        generationConfig = GeminiGenerationConfig(temperature = 0.7),
                        tools = standardTools
                    )

                    val directBody = json.encodeToString(requestPayload)
                    val candidates = mutableListOf<EndpointCandidate>()

                    if (isApiKey) {
                        candidates.add(
                            EndpointCandidate(
                                url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$token",
                                jsonBody = directBody,
                                headers = mapOf("Accept" to "text/event-stream"),
                                description = "Generative Language API ($model)"
                            )
                        )
                        if (!model.startsWith("gemini-2.5") && !model.startsWith("gemini-2.0") && !model.startsWith("gemini-1.5")) {
                            candidates.add(
                                EndpointCandidate(
                                    url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$token",
                                    jsonBody = directBody,
                                    headers = mapOf("Accept" to "text/event-stream"),
                                    description = "Generative Language API Fallback (gemini-2.5-flash)"
                                )
                            )
                        }
                    } else {
                        val boqPayload = json.encodeToString(
                            AntigravityBoQRequest(
                                project = "aicode-consumers",
                                model = model,
                                userAgent = "antigravity",
                                requestType = "agent",
                                request = requestPayload
                            )
                        )
                        candidates.add(
                            EndpointCandidate(
                                url = "https://cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse",
                                jsonBody = boqPayload,
                                headers = mapOf(
                                    "Accept" to "text/event-stream",
                                    "Authorization" to "Bearer $token",
                                    "User-Agent" to "antigravity/cli/1.2.14 (aidev_client; os_type=android; arch=arm64)"
                                ),
                                description = "Cloud Code Production Gateway (cloudcode-pa)"
                            )
                        )
                        candidates.add(
                            EndpointCandidate(
                                url = "https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse",
                                jsonBody = boqPayload,
                                headers = mapOf(
                                    "Accept" to "text/event-stream",
                                    "Authorization" to "Bearer $token",
                                    "User-Agent" to "antigravity/cli/1.2.14 (aidev_client; os_type=android; arch=arm64)"
                                ),
                                description = "Cloud Code Daily Gateway (daily-cloudcode-pa)"
                            )
                        )
                        candidates.add(
                            EndpointCandidate(
                                url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse",
                                jsonBody = directBody,
                                headers = mapOf(
                                    "Accept" to "text/event-stream",
                                    "Authorization" to "Bearer $token"
                                ),
                                description = "Generative Language API OAuth ($model)"
                            )
                        )
                        if (!model.startsWith("gemini-2.5") && !model.startsWith("gemini-2.0") && !model.startsWith("gemini-1.5")) {
                            candidates.add(
                                EndpointCandidate(
                                    url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
                                    jsonBody = directBody,
                                    headers = mapOf(
                                        "Accept" to "text/event-stream",
                                        "Authorization" to "Bearer $token"
                                    ),
                                    description = "Generative Language API Fallback (gemini-2.5-flash)"
                                )
                            )
                        }
                    }

                    for ((index, candidate) in candidates.withIndex()) {
                        if (!isActive) break
                        log("Turn $currentTurn: Endpoint [${index + 1}/${candidates.size}] trying ${candidate.description}")
                        val reqBuilder = Request.Builder()
                            .url(candidate.url)
                            .post(candidate.jsonBody.toRequestBody("application/json".toMediaType()))
                        candidate.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

                        val call = okHttpClient.newCall(reqBuilder.build())
                        activeCalls[sessionId] = call

                        try {
                            call.execute().use { response ->
                                log("Turn $currentTurn: Endpoint [${index + 1}/${candidates.size}] ${candidate.description} responded HTTP ${response.code} ${response.message}")
                                if (!response.isSuccessful) {
                                    val errBody = response.body?.string().orEmpty().take(300)
                                    val detail = "HTTP ${response.code}: $errBody"
                                    log("Error from ${candidate.description}: $detail")
                                    attemptSummary.append("• ${candidate.description}: $detail\n")
                                    return@use
                                }

                                val source = response.body?.source()
                                if (source == null) {
                                    log("Error: empty response body from ${candidate.description}")
                                    attemptSummary.append("• ${candidate.description}: Пустой ответ сервера\n")
                                    return@use
                                }

                                while (!source.exhausted() && isActive) {
                                    val line = source.readUtf8Line() ?: break
                                    if (line.startsWith("data:")) {
                                        val jsonLine = line.removePrefix("data:").trim()
                                        if (jsonLine.isNotEmpty()) {
                                            try {
                                                val chunk = json.decodeFromString<GeminiStreamChunk>(jsonLine)
                                                val candidateChunk = (chunk.response?.candidates ?: chunk.candidates)?.firstOrNull()
                                                candidateChunk?.content?.parts?.forEach { part ->
                                                    val text = part.text
                                                    if (!text.isNullOrEmpty()) {
                                                        if (chunkCount == 0) {
                                                            log("First streaming chunk received from ${candidate.description} in turn $currentTurn")
                                                        }
                                                        chunkCount++
                                                        if (part.thought == true) {
                                                            _eventFlow.emit(AgentEvent.ReasoningDelta(assistantMsgId, text))
                                                        } else {
                                                            turnTextAccumulated.append(text)
                                                            _eventFlow.emit(AgentEvent.TextDelta(assistantMsgId, text))
                                                        }
                                                    }
                                                    part.functionCall?.let { fCall ->
                                                        log("Function call received: ${fCall.name}")
                                                        turnToolCalls.add(fCall)
                                                        chunkCount++
                                                    }
                                                }
                                            } catch (_: Exception) {
                                                // Non-fatal parse glitch on keep-alive or malformed chunk
                                            }
                                        }
                                    }
                                }

                                if (chunkCount > 0) {
                                    turnSuccess = true
                                    lastStreamSuccess = true
                                    log("Stream completed successfully via ${candidate.description} in turn $currentTurn ($chunkCount parts)")
                                } else {
                                    log("Warning: stream closed without delivering any content parts")
                                    attemptSummary.append("• ${candidate.description}: Соединение закрылось без данных\n")
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val detail = "${e.javaClass.simpleName}: ${e.message}"
                            log("Exception during ${candidate.description}: $detail")
                            attemptSummary.append("• ${candidate.description}: $detail\n")
                        }

                        if (turnSuccess) {
                            break
                        }
                    }

                    if (!turnSuccess) {
                        log("Turn $currentTurn failed across all endpoints")
                        break
                    }

                    if (turnToolCalls.isEmpty()) {
                        // Response completed without requiring further tools
                        log("Session $sessionId response completed in turn $currentTurn")
                        _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.IDLE))
                        return@launch
                    }

                    // Append model's tool calls to conversation history
                    val modelParts = mutableListOf<GeminiPart>()
                    if (turnTextAccumulated.isNotEmpty()) {
                        modelParts.add(GeminiPart(text = turnTextAccumulated.toString()))
                    }
                    turnToolCalls.forEach { fCall ->
                        modelParts.add(GeminiPart(functionCall = fCall))
                    }
                    conversationContents.add(
                        GeminiContent(
                            role = "model",
                            parts = modelParts
                        )
                    )

                    // Execute each tool and emit AgentEvent.ToolCallUpdate
                    log("Turn $currentTurn: executing ${turnToolCalls.size} tool call(s)")
                    val functionResponseParts = mutableListOf<GeminiPart>()
                    for (fCall in turnToolCalls) {
                        val callId = "call_${UUID.randomUUID().toString().take(8)}"
                        val argsJson = fCall.args?.toString()

                        _eventFlow.emit(
                            AgentEvent.ToolCallUpdate(
                                messageId = assistantMsgId,
                                callId = callId,
                                name = fCall.name,
                                status = ToolStatus.RUNNING,
                                input = argsJson,
                                output = null
                            )
                        )

                        val output = try {
                            executeTool(fCall.name, fCall.args, workspaceDir)
                        } catch (e: Exception) {
                            "Tool execution failed: ${e.message}"
                        }

                        val status = if (output.startsWith("Error:") || output.startsWith("Tool execution failed:")) {
                            ToolStatus.ERROR
                        } else {
                            ToolStatus.COMPLETED
                        }

                        _eventFlow.emit(
                            AgentEvent.ToolCallUpdate(
                                messageId = assistantMsgId,
                                callId = callId,
                                name = fCall.name,
                                status = status,
                                input = argsJson,
                                output = output
                            )
                        )

                        val responseObj = buildJsonObject {
                            put("output", output)
                        }
                        functionResponseParts.add(
                            GeminiPart(
                                functionResponse = GeminiFunctionResponse(
                                    name = fCall.name,
                                    response = responseObj
                                )
                            )
                        )
                    }

                    // Append function responses to conversationContents for next turn
                    conversationContents.add(
                        GeminiContent(
                            role = "function",
                            parts = functionResponseParts
                        )
                    )
                }

                if (lastStreamSuccess) {
                    _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.IDLE))
                    return@launch
                }

                val explanation = """
❌ **Не удалось получить ответ от Google Antigravity**

**Результаты обращения к серверам Google:**
$attemptSummary
💡 **Рекомендация:**
1. Если вы авторизовались через Google-аккаунт, доступ к корпоративным шлюзам Cloud Code (`cloudcode-pa`) может требовать проект Google Cloud.
2. Самый надежный способ: получите бесплатный API-ключ в [Google AI Studio](https://aistudio.google.com/app/apikey) и укажите его в **Настройки -> Gemini / Antigravity -> Ввести API-ключ**.
3. Подробный журнал запросов доступен в **Настройки -> Просмотр логов**.
""".trimIndent()

                log("All endpoints failed. Emitting explanation to chat session $sessionId.")
                _eventFlow.emit(AgentEvent.TextDelta(assistantMsgId, explanation))
                _eventFlow.emit(AgentEvent.Error("Все эндпоинты Antigravity вернули ошибку", recoverable = true))
                _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.ERROR))
            } catch (e: CancellationException) {
                log("Session $sessionId cancelled")
                _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.IDLE))
                throw e
            } catch (e: Exception) {
                val err = e.message ?: "Неизвестная ошибка Antigravity"
                log("sendPrompt unexpected exception: $err")
                _eventFlow.emit(AgentEvent.TextDelta(assistantMsgId, "\n\n❌ **Ошибка:** $err"))
                _eventFlow.emit(AgentEvent.Error(err, recoverable = true))
                _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.ERROR))
            } finally {
                activeCalls.remove(sessionId)
                activeJobs.remove(sessionId)
            }
        }
        activeJobs[sessionId] = job
        return Result.success(Unit)
    }

    private suspend fun executeTool(
        name: String,
        args: JsonObject?,
        workspaceDir: String?
    ): String {
        return when (name.lowercase()) {
            "web_search", "google_search" -> {
                val query = args?.get("query")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (query.isBlank()) {
                    "Error: 'query' parameter is required for web_search"
                } else {
                    executeWebSearch(query)
                }
            }
            "bash", "terminal", "command" -> {
                val command = args?.get("command")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (command.isBlank()) {
                    "Error: 'command' parameter is required for bash"
                } else {
                    executeBash(command, workspaceDir)
                }
            }
            "read_file" -> {
                val path = args?.get("path")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (path.isBlank()) {
                    "Error: 'path' parameter is required for read_file"
                } else {
                    executeReadFile(path, workspaceDir)
                }
            }
            "write_file" -> {
                val path = args?.get("path")?.jsonPrimitive?.contentOrNull.orEmpty()
                val content = args?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (path.isBlank()) {
                    "Error: 'path' parameter is required for write_file"
                } else {
                    executeWriteFile(path, content, workspaceDir)
                }
            }
            "list_dir" -> {
                val path = args?.get("path")?.jsonPrimitive?.contentOrNull
                executeListDir(path, workspaceDir)
            }
            else -> {
                "Error: Unknown tool '$name'"
            }
        }
    }

    suspend fun executeWebSearch(query: String): String = withContext(Dispatchers.IO) {
        log("executeWebSearch: query='$query'")
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Web search HTTP error: ${response.code} ${response.message}"
                }
                val html = response.body?.string().orEmpty()
                if (html.isEmpty()) {
                    return@withContext "Web search returned empty response"
                }

                val results = parseDuckDuckGoHtml(html)
                if (results.isEmpty()) {
                    return@withContext "No web search results found for query: '$query'"
                }

                buildString {
                    appendLine("Web search results for: \"$query\"\n")
                    results.take(5).forEachIndexed { idx, res ->
                        appendLine("${idx + 1}. ${res.title}")
                        if (res.snippet.isNotBlank()) {
                            appendLine("   Snippet: ${res.snippet}")
                        }
                        if (res.url.isNotBlank()) {
                            appendLine("   URL: ${res.url}")
                        }
                        appendLine()
                    }
                }.trim()
            }
        } catch (e: Exception) {
            log("executeWebSearch error: ${e.message}")
            "Web search failed: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun parseDuckDuckGoHtml(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val titleRegex = Regex("""<h2 class="result__title">[\s\S]*?<a[^>]*href="([^"]*)"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        val snippetRegex = Regex("""<a class="result__snippet"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)

        val titleMatches = titleRegex.findAll(html).toList()
        val snippetMatches = snippetRegex.findAll(html).toList()

        val count = minOf(titleMatches.size, 10)
        for (i in 0 until count) {
            val rawHref = titleMatches[i].groupValues.getOrNull(1).orEmpty()
            val rawTitle = titleMatches[i].groupValues.getOrNull(2).orEmpty()
            val rawSnippet = snippetMatches.getOrNull(i)?.groupValues?.getOrNull(1).orEmpty()

            val cleanTitle = cleanHtmlText(rawTitle)
            val cleanSnippet = cleanHtmlText(rawSnippet)
            var cleanUrl = rawHref
            if (cleanUrl.contains("uddg=")) {
                try {
                    val uddgParam = cleanUrl.substringAfter("uddg=").substringBefore("&")
                    cleanUrl = URLDecoder.decode(uddgParam, "UTF-8")
                } catch (_: Exception) {}
            }
            if (cleanTitle.isNotBlank()) {
                results.add(SearchResult(title = cleanTitle, snippet = cleanSnippet, url = cleanUrl))
            }
        }
        return results
    }

    private fun cleanHtmlText(text: String): String {
        return text.replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun executeBash(command: String, directory: String?): String = withContext(Dispatchers.IO) {
        log("executeBash: command='$command' dir='$directory'")
        try {
            val workingDir = if (!directory.isNullOrBlank()) {
                val f = java.io.File(directory)
                if (f.exists() && f.isDirectory) f else null
            } else null

            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            val processBuilder = if (isWindows) {
                ProcessBuilder("cmd.exe", "/c", command)
            } else {
                val shPath = if (java.io.File("/system/bin/sh").exists()) "/system/bin/sh" else "/bin/sh"
                ProcessBuilder(shPath, "-c", command)
            }

            if (workingDir != null) {
                processBuilder.directory(workingDir)
            }

            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            val output = StringBuilder()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            var line: String?
            val maxChars = 32_000
            while (reader.readLine().also { line = it } != null) {
                if (output.length < maxChars) {
                    output.appendLine(line)
                } else if (!output.endsWith("... [output truncated]\n")) {
                    output.appendLine("... [output truncated]")
                }
            }

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                output.appendLine("\n[Process timed out after 30 seconds]")
            }

            val exitCode = if (completed) process.exitValue() else -1
            if (output.isEmpty()) {
                output.append("[Command completed with exit code $exitCode (no output)]")
            } else {
                output.appendLine("\n[Exit code: $exitCode]")
            }
            output.toString().trim()
        } catch (e: Exception) {
            log("executeBash error: ${e.message}")
            "Bash execution error: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun resolvePath(path: String, directory: String?): java.io.File {
        val file = java.io.File(path)
        return if (file.isAbsolute) {
            file
        } else if (!directory.isNullOrBlank()) {
            java.io.File(directory, path)
        } else {
            file
        }
    }

    private suspend fun executeReadFile(path: String, directory: String?): String = withContext(Dispatchers.IO) {
        try {
            val target = resolvePath(path, directory)
            if (!target.exists()) {
                return@withContext "File not found: ${target.absolutePath}"
            }
            if (target.isDirectory) {
                return@withContext "Path is a directory, not a file: ${target.absolutePath}. Use list_dir instead."
            }
            val maxBytes = 64 * 1024L
            val content = if (target.length() > maxBytes) {
                target.inputStream().use { stream ->
                    val bytes = ByteArray(maxBytes.toInt())
                    val read = stream.read(bytes)
                    String(bytes, 0, read) + "\n\n... [File truncated: showing first 64KB of ${target.length()} bytes]"
                }
            } else {
                target.readText(Charsets.UTF_8)
            }
            content
        } catch (e: Exception) {
            "Failed to read file '$path': ${e.localizedMessage ?: e.message}"
        }
    }

    private suspend fun executeWriteFile(path: String, content: String, directory: String?): String = withContext(Dispatchers.IO) {
        try {
            val target = resolvePath(path, directory)
            target.parentFile?.mkdirs()
            target.writeText(content, Charsets.UTF_8)
            "File successfully written: ${target.absolutePath} (${content.length} characters)"
        } catch (e: Exception) {
            "Failed to write file '$path': ${e.localizedMessage ?: e.message}"
        }
    }

    private suspend fun executeListDir(path: String?, directory: String?): String = withContext(Dispatchers.IO) {
        try {
            val target = if (path.isNullOrBlank()) {
                if (!directory.isNullOrBlank()) java.io.File(directory) else java.io.File(".")
            } else {
                resolvePath(path, directory)
            }

            if (!target.exists()) {
                return@withContext "Directory not found: ${target.absolutePath}"
            }
            if (!target.isDirectory) {
                return@withContext "Path is not a directory: ${target.absolutePath}"
            }

            val entries = target.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
            if (entries.isEmpty()) {
                return@withContext "Directory ${target.absolutePath} is empty"
            }

            buildString {
                appendLine("Contents of ${target.absolutePath}:")
                for (entry in entries.take(100)) {
                    val type = if (entry.isDirectory) "[DIR]" else "[FILE]"
                    val size = if (entry.isDirectory) "" else " (${entry.length()} bytes)"
                    appendLine("$type ${entry.name}$size")
                }
                if (entries.size > 100) {
                    appendLine("... and ${entries.size - 100} more items")
                }
            }.trim()
        } catch (e: Exception) {
            "Failed to list directory: ${e.localizedMessage ?: e.message}"
        }
    }

    override suspend fun getModels(): Result<List<ModelInfo>> {
        return Result.success(
            listOf(
                ModelInfo("google", "gemini-2.5-flash", "Gemini 2.5 Flash (Быстрая, мультимодальная, поиск и инструменты)"),
                ModelInfo("google", "gemini-2.5-pro", "Gemini 2.5 Pro (Глубокое рассуждение, кодинг)"),
                ModelInfo("google", "gemini-3.8-flash-high", "Gemini 3.8 Flash (High Reasoning - Флагман 2026)"),
                ModelInfo("google", "gemini-3.8-flash-medium", "Gemini 3.8 Flash (Medium Reasoning)"),
                ModelInfo("google", "gemini-3.8-flash-low", "Gemini 3.8 Flash (Low Latency)"),
                ModelInfo("google", "gemini-3.7-flash-high", "Gemini 3.7 Flash (High Reasoning)"),
                ModelInfo("google", "gemini-3.6-flash-high", "Gemini 3.6 Flash (High Reasoning)"),
                ModelInfo("google", "gemini-3.1-pro-high", "Gemini 3.1 Pro (Advanced Reasoning)"),
                ModelInfo("anthropic", "claude-sonnet-4-6", "Claude Sonnet 4.6 (Thinking)"),
                ModelInfo("anthropic", "claude-opus-4-6-thinking", "Claude Opus 4.6 (Deep Thinking)"),
                ModelInfo("openai", "gpt-oss-120b-medium", "GPT-OSS 120B (Medium)")
            )
        )
    }

    override suspend fun cancel(sessionId: String): Result<Unit> {
        activeCalls[sessionId]?.cancel()
        activeJobs[sessionId]?.cancel()
        _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.IDLE))
        return Result.success(Unit)
    }

    override suspend fun respondPermission(requestId: String, option: PermissionOption): Result<Unit> {
        return underlyingAdapter?.respondPermission(requestId, option) ?: Result.success(Unit)
    }

    override suspend fun disconnect() {
        activeCalls.values.forEach { it.cancel() }
        activeJobs.values.forEach { it.cancel() }
        activeCalls.clear()
        activeJobs.clear()
    }
}
