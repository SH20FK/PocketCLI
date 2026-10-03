package com.pocketcli.data.opencode.antigravity

import com.pocketcli.core.model.*
import com.pocketcli.core.security.AntigravityAuthManager
import com.pocketcli.data.local.db.AppDatabase
import com.pocketcli.data.local.db.SessionEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    val thought: Boolean? = null
)

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = 0.7
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val index: Int? = null
)

@Serializable
data class GeminiStreamChunk(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : AgentAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
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

    override suspend fun connect(): Result<Unit> {
        val tokenRes = authManager.getValidAccessToken()
        return if (tokenRes.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(tokenRes.exceptionOrNull() ?: IllegalStateException("Google Antigravity не авторизован"))
        }
    }

    override fun events(sessionId: String): Flow<AgentEvent> {
        return _eventFlow.asSharedFlow()
    }

    override suspend fun createSession(title: String, directory: String?): Result<Session> {
        val sessionId = "antigravity_${UUID.randomUUID().toString().take(8)}"
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
            ?: authManager.state.value.selectedModel.ifEmpty { "gemini-2.5-pro" }

        val activeJob = activeJobs[sessionId]
        if (activeJob?.isActive == true) {
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
                    _eventFlow.emit(AgentEvent.Error(errorMsg, recoverable = true))
                    _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.ERROR))
                    return@launch
                }
                val token = tokenResult.getOrThrow()

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
                // Append current user prompt
                conversationContents.add(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = prompt.text))
                    )
                )

                val requestPayload = GeminiGenerateRequest(
                    contents = conversationContents,
                    systemInstruction = GeminiContent(
                        role = "system",
                        parts = listOf(
                            GeminiPart(
                                text = "You are Antigravity, an advanced AI coding assistant developed by Google DeepMind. You write clean, robust, production-ready code, follow best engineering practices, and provide structured reasoning when thinking through complex problems."
                            )
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(temperature = 0.7)
                )

                val jsonBody = json.encodeToString(requestPayload)
                val isApiKey = token.startsWith("AIza")

                val url = if (isApiKey) {
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$token"
                } else {
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "text/event-stream")

                if (!isApiKey) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val call = okHttpClient.newCall(requestBuilder.build())
                activeCalls[sessionId] = call

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty()
                        val errMsg = "Ошибка Gemini API (HTTP ${response.code}): $errBody"
                        _eventFlow.emit(AgentEvent.Error(errMsg, recoverable = true))
                        _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.ERROR))
                        return@launch
                    }

                    val source = response.body?.source() ?: return@launch
                    while (!source.exhausted() && isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val jsonLine = line.removePrefix("data:").trim()
                            if (jsonLine.isNotEmpty()) {
                                try {
                                    val chunk = json.decodeFromString<GeminiStreamChunk>(jsonLine)
                                    val candidate = chunk.candidates?.firstOrNull()
                                    candidate?.content?.parts?.forEach { part ->
                                        val text = part.text
                                        if (!text.isNullOrEmpty()) {
                                            if (part.thought == true) {
                                                _eventFlow.emit(AgentEvent.ReasoningDelta(assistantMsgId, text))
                                            } else {
                                                _eventFlow.emit(AgentEvent.TextDelta(assistantMsgId, text))
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Non-fatal parse glitch on keep-alive or malformed chunk
                                }
                            }
                        }
                    }
                }

                _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.IDLE))
            } catch (e: CancellationException) {
                _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.IDLE))
                throw e
            } catch (e: Exception) {
                _eventFlow.emit(AgentEvent.Error(e.message ?: "Неизвестная ошибка Antigravity", recoverable = true))
                _eventFlow.emit(AgentEvent.SessionStatus(sessionId, SessionState.ERROR))
            } finally {
                activeCalls.remove(sessionId)
                activeJobs.remove(sessionId)
            }
        }
        activeJobs[sessionId] = job
        return Result.success(Unit)
    }

    override suspend fun getModels(): Result<List<ModelInfo>> {
        return Result.success(
            listOf(
                ModelInfo("google", "gemini-2.5-pro", "Gemini 2.5 Pro (DeepMind Flagship)"),
                ModelInfo("google", "gemini-2.5-flash", "Gemini 2.5 Flash (Ultra-Fast Coding)"),
                ModelInfo("google", "gemini-2.0-flash-thinking", "Gemini 2.0 Flash Thinking (Deep Reasoning)"),
                ModelInfo("google", "gemini-2.0-flash", "Gemini 2.0 Flash (General Purpose)")
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
