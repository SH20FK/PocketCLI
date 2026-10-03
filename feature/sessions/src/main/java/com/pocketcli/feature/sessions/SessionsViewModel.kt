package com.pocketcli.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.Session
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import com.pocketcli.data.opencode.repository.AgentSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionsUiState(
    val activeProfileId: String = "",
    val activeProfileName: String = "",
    val sessions: List<Session> = emptyList(),
    val isCreatingSession: Boolean = false,
    val showCreateDialog: Boolean = false,
    val newSessionTitle: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: AgentSessionRepository,
    private val connectionManager: ActiveConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.activeProfile.collectLatest { profile ->
                _uiState.update {
                    it.copy(
                        activeProfileId = profile?.id.orEmpty(),
                        activeProfileName = profile?.name.orEmpty(),
                        errorMessage = null
                    )
                }
                if (profile != null) {
                    launch {
                        repository.getSessions(profile.id).collect { list ->
                            _uiState.update { it.copy(sessions = list) }
                        }
                    }
                    syncSessions(profile.id)
                } else {
                    _uiState.update { it.copy(sessions = emptyList()) }
                }
            }
        }
    }

    fun syncSessions(profileId: String) {
        viewModelScope.launch {
            val adapter = connectionManager.getAdapter() ?: return@launch
            adapter.listSessions().onSuccess { remoteSessions ->
                for (session in remoteSessions) {
                    repository.saveSession(session)
                }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, newSessionTitle = "", errorMessage = null) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, errorMessage = null) }
    }

    fun onNewSessionTitleChange(title: String) {
        _uiState.update { it.copy(newSessionTitle = title, errorMessage = null) }
    }

    fun createSession(onCreated: (String) -> Unit) {
        val title = _uiState.value.newSessionTitle.ifBlank { "New Session" }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSession = true, errorMessage = null) }
            val adapter = connectionManager.getAdapter()
            if (adapter == null) {
                _uiState.update {
                    it.copy(
                        isCreatingSession = false,
                        errorMessage = "No active connection profile. Please add one in Settings."
                    )
                }
                return@launch
            }

            val result = adapter.createSession(title)
            result.fold(
                onSuccess = { session ->
                    repository.saveSession(session)
                    _uiState.update { it.copy(isCreatingSession = false, showCreateDialog = false, newSessionTitle = "") }
                    onCreated(session.id)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCreatingSession = false,
                            errorMessage = error.localizedMessage ?: error.message ?: "Failed to connect to agent server"
                        )
                    }
                }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        val profileId = _uiState.value.activeProfileId
        if (profileId.isNotEmpty()) {
            viewModelScope.launch {
                repository.deleteSession(profileId, sessionId)
            }
        }
    }
}
