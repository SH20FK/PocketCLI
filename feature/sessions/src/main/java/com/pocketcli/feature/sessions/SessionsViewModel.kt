package com.pocketcli.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.AgentType
import com.pocketcli.core.model.Session
import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.data.local.repository.WorkspaceRepository
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
    val workspaces: List<Workspace> = emptyList(),
    val workspacesWithDetails: List<WorkspaceWithDetails> = emptyList(),
    val activeSessionIds: Set<String> = emptySet(),
    val selectedWorkspaceId: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isCreatingSession: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showProjectPicker: Boolean = false,
    val newSessionTitle: String = "",
    val selectedAgentType: AgentType = AgentType.OPENCODE,
    val errorMessage: String? = null
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val repository: AgentSessionRepository,
    private val connectionManager: ActiveConnectionManager,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    private var lastDeletedSession: Session? = null

    init {
        viewModelScope.launch {
            repository.getActiveSessionIds().collect { activeIds ->
                _uiState.update { it.copy(activeSessionIds = activeIds) }
            }
        }
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
                    launch {
                        workspaceRepository.getWorkspaces(profile.id).collect { list ->
                            _uiState.update { it.copy(workspaces = list) }
                        }
                    }
                    launch {
                        workspaceRepository.getWorkspacesWithDetails(profile.id).collect { list ->
                            _uiState.update { it.copy(workspacesWithDetails = list) }
                        }
                    }
                    syncSessions(profile.id)
                } else {
                    _uiState.update {
                        it.copy(
                            sessions = emptyList(),
                            workspaces = emptyList(),
                            workspacesWithDetails = emptyList(),
                            selectedWorkspaceId = null
                        )
                    }
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

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active, searchQuery = if (!active) "" else it.searchQuery) }
    }

    fun openCreateDialog(preselectedWorkspaceId: String? = null) {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                newSessionTitle = "",
                selectedWorkspaceId = preselectedWorkspaceId ?: it.selectedWorkspaceId,
                errorMessage = null
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, errorMessage = null) }
    }

    fun openProjectPicker() {
        _uiState.update { it.copy(showProjectPicker = true) }
    }

    fun dismissProjectPicker() {
        _uiState.update { it.copy(showProjectPicker = false) }
    }

    fun onNewSessionTitleChange(title: String) {
        _uiState.update { it.copy(newSessionTitle = title, errorMessage = null) }
    }

    fun selectWorkspace(workspaceId: String?) {
        _uiState.update { it.copy(selectedWorkspaceId = workspaceId, showProjectPicker = false) }
    }

    fun selectAgentType(agentType: AgentType) {
        if (!agentType.isAvailable) return
        _uiState.update { it.copy(selectedAgentType = agentType) }
    }

    fun createSession(onCreated: (String) -> Unit) {
        val title = _uiState.value.newSessionTitle.ifBlank { "Новый чат" }
        val selectedWsId = _uiState.value.selectedWorkspaceId
        val selectedWorkspace = _uiState.value.workspaces.find { it.id == selectedWsId }
        val selectedAgent = _uiState.value.selectedAgentType

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSession = true, errorMessage = null) }
            val adapter = connectionManager.getAdapterFor(selectedAgent) ?: connectionManager.getAdapter()
            if (adapter == null) {
                _uiState.update {
                    it.copy(
                        isCreatingSession = false,
                        errorMessage = "Нет активного профиля подключения. Настройте его в Настройках."
                    )
                }
                return@launch
            }

            val result = adapter.createSession(title, directory = selectedWorkspace?.localPath)
            result.fold(
                onSuccess = { session ->
                    val sessionWithWorkspace = session.copy(
                        workspaceId = selectedWsId,
                        agentType = selectedAgent
                    )
                    repository.saveSession(sessionWithWorkspace)
                    selectedWsId?.let { wsId ->
                        workspaceRepository.touchWorkspace(wsId)
                    }
                    _uiState.update {
                        it.copy(
                            isCreatingSession = false,
                            showCreateDialog = false,
                            newSessionTitle = ""
                        )
                    }
                    onCreated(session.id)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCreatingSession = false,
                            errorMessage = error.localizedMessage ?: error.message ?: "Ошибка подключения к серверу"
                        )
                    }
                }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        val profileId = _uiState.value.activeProfileId
        if (profileId.isNotEmpty()) {
            val session = _uiState.value.sessions.find { it.id == sessionId }
            lastDeletedSession = session
            viewModelScope.launch {
                repository.deleteSession(profileId, sessionId)
            }
        }
    }

    fun undoDeleteSession() {
        val session = lastDeletedSession ?: return
        viewModelScope.launch {
            repository.saveSession(session)
            lastDeletedSession = null
        }
    }
}
