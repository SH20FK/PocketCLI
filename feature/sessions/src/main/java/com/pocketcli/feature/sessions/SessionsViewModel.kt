package com.pocketcli.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.Session
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.repository.AgentSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionsUiState(
    val activeProfileId: String = "",
    val sessions: List<Session> = emptyList(),
    val isCreatingSession: Boolean = false,
    val showCreateDialog: Boolean = false,
    val newSessionTitle: String = ""
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: AgentSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private var activeAdapter: OpenCodeAdapter? = null

    fun initialize(profileId: String, adapter: OpenCodeAdapter) {
        _uiState.update { it.copy(activeProfileId = profileId) }
        activeAdapter = adapter

        viewModelScope.launch {
            repository.getSessions(profileId).collect { list ->
                _uiState.update { it.copy(sessions = list) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, newSessionTitle = "") }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun onNewSessionTitleChange(title: String) {
        _uiState.update { it.copy(newSessionTitle = title) }
    }

    fun createSession(onCreated: (String) -> Unit) {
        val title = _uiState.value.newSessionTitle.ifBlank { "New Session" }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSession = true) }
            val adapter = activeAdapter ?: return@launch
            val result = adapter.createSession(title)
            _uiState.update { it.copy(isCreatingSession = false, showCreateDialog = false) }

            result.onSuccess { session ->
                repository.saveSession(session)
                onCreated(session.id)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        val profileId = _uiState.value.activeProfileId
        viewModelScope.launch {
            repository.deleteSession(profileId, sessionId)
        }
    }
}
