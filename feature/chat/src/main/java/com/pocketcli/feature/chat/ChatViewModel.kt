package com.pocketcli.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.*
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import com.pocketcli.data.opencode.repository.AgentSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val sessionId: String = "",
    val profileId: String = "",
    val sessionTitle: String = "Чат сессии",
    val workspaceName: String = "Локально",
    val messages: List<Message> = emptyList(),
    val sessionState: SessionState = SessionState.IDLE,
    val pendingPermission: AgentEvent.PermissionRequested? = null,
    val showPermissionDetails: Boolean = false,
    val selectedToolForDetails: ToolCall? = null,
    val activeDiffFile: Pair<String, String>? = null,
    val composerDraft: String = "",
    val attachments: List<String> = emptyList(),
    val availableModels: List<ModelInfo> = emptyList(),
    val selectedModel: ModelInfo? = null,
    val isModelPickerOpen: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AgentSessionRepository,
    private val connectionManager: ActiveConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeAdapter: OpenCodeAdapter? = null

    fun initialize(sessionId: String, profileId: String? = null, adapter: OpenCodeAdapter? = null) {
        val effectiveAdapter = adapter ?: connectionManager.getAdapter()
        activeAdapter = effectiveAdapter

        viewModelScope.launch {
            val resolvedProfileId = profileId?.takeIf { it.isNotEmpty() }
                ?: connectionManager.activeProfile.value?.id
                ?: repository.getProfileIdForSession(sessionId)
                ?: connectionManager.getActiveProfileId()
                ?: ""

            val session = repository.getSession(resolvedProfileId, sessionId)
            val title = session?.title?.ifBlank { "Чат сессии" } ?: "Чат сессии"

            _uiState.update {
                it.copy(
                    sessionId = sessionId,
                    profileId = resolvedProfileId,
                    sessionTitle = title
                )
            }

            // 1. Observe hybrid message stream (Room + in-flight StateFlow)
            launch {
                repository.getMessages(resolvedProfileId, sessionId).collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
            }

            // 2. Load available models from the server
            launch {
                effectiveAdapter?.getModels()?.onSuccess { models ->
                    _uiState.update { it.copy(availableModels = models) }
                }
            }

            // 3. Reconcile existing server messages if available
            val apiClient = effectiveAdapter?.apiClient
            if (apiClient != null && resolvedProfileId.isNotEmpty()) {
                launch {
                    repository.reconcile(apiClient, resolvedProfileId, sessionId)
                }
            }

            // 4. Observe SSE events for this session
            if (effectiveAdapter != null) {
                launch {
                    effectiveAdapter.events(sessionId).collect { event ->
                        repository.handleAgentEvent(resolvedProfileId, sessionId, event)

                        when (event) {
                            is AgentEvent.SessionStatus -> {
                                _uiState.update { it.copy(sessionState = event.state) }
                            }
                            is AgentEvent.PermissionRequested -> {
                                _uiState.update { it.copy(pendingPermission = event) }
                            }
                            is AgentEvent.FileDiff -> {
                                _uiState.update { it.copy(activeDiffFile = Pair(event.path, event.unifiedDiff)) }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    fun onDraftChange(text: String) {
        _uiState.update { it.copy(composerDraft = text) }
    }

    fun addAttachment(name: String) {
        _uiState.update { it.copy(attachments = it.attachments + name) }
    }

    fun removeAttachment(index: Int) {
        _uiState.update {
            val list = it.attachments.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
            }
            it.copy(attachments = list)
        }
    }

    fun openModelPicker() {
        _uiState.update { it.copy(isModelPickerOpen = true) }
    }

    fun dismissModelPicker() {
        _uiState.update { it.copy(isModelPickerOpen = false) }
    }

    fun selectModel(model: ModelInfo?) {
        _uiState.update { it.copy(selectedModel = model, isModelPickerOpen = false) }
    }

    fun openPermissionDetails() {
        _uiState.update { it.copy(showPermissionDetails = true) }
    }

    fun dismissPermissionDetails() {
        _uiState.update { it.copy(showPermissionDetails = false) }
    }

    fun openDiff(path: String, diff: String) {
        _uiState.update { it.copy(activeDiffFile = Pair(path, diff)) }
    }

    fun closeDiff() {
        _uiState.update { it.copy(activeDiffFile = null) }
    }

    fun sendPrompt() {
        val state = _uiState.value
        val text = state.composerDraft.trim()
        if (text.isBlank() || state.sessionState == SessionState.BUSY) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    composerDraft = "",
                    attachments = emptyList(),
                    sessionState = SessionState.BUSY,
                    errorMessage = null
                )
            }
            // 1. Record user message in DB
            repository.recordUserMessage(state.profileId, state.sessionId, text)

            // 2. Dispatch to agent adapter
            val adapter = activeAdapter ?: connectionManager.getAdapter()
            if (adapter == null) {
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.IDLE,
                        errorMessage = "Нет активного подключения. Проверьте настройки."
                    )
                }
                return@launch
            }

            val modelInput = state.selectedModel?.let { ModelIdentifier(it.providerId, it.modelId) }
            val result = adapter.sendPrompt(state.sessionId, Prompt(text = text, model = modelInput))
            result.onFailure { err ->
                _uiState.update {
                    it.copy(
                        sessionState = SessionState.IDLE,
                        errorMessage = "Не удалось отправить сообщение: ${err.message}"
                    )
                }
                return@launch
            }

            // 3. Reconcile with server to guarantee response is stored even if SSE dropped
            val apiClient = adapter.apiClient
            if (state.profileId.isNotEmpty()) {
                repository.reconcile(apiClient, state.profileId, state.sessionId)
            }
            _uiState.update { it.copy(sessionState = SessionState.IDLE) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun stop() {
        val state = _uiState.value
        viewModelScope.launch {
            val adapter = activeAdapter ?: connectionManager.getAdapter()
            adapter?.cancel(state.sessionId)
            _uiState.update { it.copy(sessionState = SessionState.IDLE) }
            repository.flushInFlightToDb(state.sessionId)
        }
    }

    fun respondPermission(requestId: String, option: PermissionOption) {
        viewModelScope.launch {
            val adapter = activeAdapter ?: connectionManager.getAdapter()
            adapter?.respondPermission(requestId, option)
            _uiState.update { it.copy(pendingPermission = null, showPermissionDetails = false) }
        }
    }

    fun openToolDetails(toolCall: ToolCall) {
        _uiState.update { it.copy(selectedToolForDetails = toolCall) }
    }

    fun dismissToolDetails() {
        _uiState.update { it.copy(selectedToolForDetails = null) }
    }
}
