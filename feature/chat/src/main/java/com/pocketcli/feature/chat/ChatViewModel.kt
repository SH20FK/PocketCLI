package com.pocketcli.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.*
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.repository.AgentSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val sessionId: String = "",
    val profileId: String = "",
    val messages: List<Message> = emptyList(),
    val sessionState: SessionState = SessionState.IDLE,
    val pendingPermission: AgentEvent.PermissionRequested? = null,
    val selectedToolForDetails: ToolCall? = null,
    val composerDraft: String = "",
    val currentModel: String = "Default"
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AgentSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeAdapter: OpenCodeAdapter? = null

    fun initialize(profileId: String, sessionId: String, adapter: OpenCodeAdapter) {
        _uiState.update { it.copy(sessionId = sessionId, profileId = profileId) }
        activeAdapter = adapter

        // 1. Observe hybrid message stream (Room + in-flight StateFlow)
        viewModelScope.launch {
            repository.getMessages(profileId, sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        // 2. Observe SSE events for this session
        viewModelScope.launch {
            adapter.events(sessionId).collect { event ->
                repository.handleAgentEvent(profileId, sessionId, event)

                when (event) {
                    is AgentEvent.SessionStatus -> {
                        _uiState.update { it.copy(sessionState = event.state) }
                    }
                    is AgentEvent.PermissionRequested -> {
                        _uiState.update { it.copy(pendingPermission = event) }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onDraftChange(text: String) {
        _uiState.update { it.copy(composerDraft = text) }
    }

    fun sendPrompt() {
        val state = _uiState.value
        val text = state.composerDraft.trim()
        if (text.isBlank() || state.sessionState == SessionState.BUSY) return

        viewModelScope.launch {
            _uiState.update { it.copy(composerDraft = "", sessionState = SessionState.BUSY) }
            // 1. Record user message in DB
            repository.recordUserMessage(state.profileId, state.sessionId, text)

            // 2. Dispatch to agent adapter
            activeAdapter?.sendPrompt(state.sessionId, Prompt(text = text))
        }
    }

    fun stop() {
        val state = _uiState.value
        viewModelScope.launch {
            activeAdapter?.cancel(state.sessionId)
            _uiState.update { it.copy(sessionState = SessionState.IDLE) }
            repository.flushInFlightToDb(state.sessionId)
        }
    }

    fun respondPermission(requestId: String, option: PermissionOption) {
        viewModelScope.launch {
            activeAdapter?.respondPermission(requestId, option)
            _uiState.update { it.copy(pendingPermission = null) }
        }
    }

    fun openToolDetails(toolCall: ToolCall) {
        _uiState.update { it.copy(selectedToolForDetails = toolCall) }
    }

    fun dismissToolDetails() {
        _uiState.update { it.copy(selectedToolForDetails = null) }
    }
}
