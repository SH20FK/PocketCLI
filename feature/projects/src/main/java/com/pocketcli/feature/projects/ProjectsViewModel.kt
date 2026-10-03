package com.pocketcli.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.data.local.repository.WorkspaceRepository
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val activeProfileId: String = "",
    val activeProfileName: String = "",
    val workspaces: List<WorkspaceWithDetails> = emptyList(),
    val archivedWorkspaces: List<Workspace> = emptyList(),
    val showArchived: Boolean = false,
    val isLoading: Boolean = false,
    val isCloning: Boolean = false,
    val isCreating: Boolean = false,
    val showCloneSheet: Boolean = false,
    val showCreateDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val connectionManager: ActiveConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.activeProfile.collectLatest { profile ->
                val profileId = profile?.id.orEmpty()
                val profileName = profile?.name.orEmpty()

                _uiState.update {
                    it.copy(
                        activeProfileId = profileId,
                        activeProfileName = profileName,
                        errorMessage = null
                    )
                }

                if (profileId.isNotEmpty()) {
                    launch {
                        workspaceRepository.getWorkspacesWithDetails(profileId).collect { list ->
                            _uiState.update { it.copy(workspaces = list) }
                        }
                    }
                    launch {
                        workspaceRepository.getArchivedWorkspaces(profileId).collect { list ->
                            _uiState.update { it.copy(archivedWorkspaces = list) }
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            workspaces = emptyList(),
                            archivedWorkspaces = emptyList()
                        )
                    }
                }
            }
        }
    }

    fun openCloneSheet() {
        _uiState.update { it.copy(showCloneSheet = true, errorMessage = null) }
    }

    fun dismissCloneSheet() {
        _uiState.update { it.copy(showCloneSheet = false, errorMessage = null) }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, errorMessage = null) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, errorMessage = null) }
    }

    fun toggleShowArchived() {
        _uiState.update { it.copy(showArchived = !it.showArchived) }
    }

    fun createWorkspace(
        displayName: String,
        initReadme: Boolean = true,
        onCreated: (Workspace) -> Unit = {}
    ) {
        val profileId = _uiState.value.activeProfileId
        if (profileId.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please configure and select a connection profile first.") }
            return
        }

        val name = displayName.trim().ifBlank { "Untitled Project" }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorMessage = null) }
            try {
                val workspace = workspaceRepository.createWorkspace(
                    profileId = profileId,
                    displayName = name,
                    sourceType = WorkspaceSourceType.CREATED,
                    initReadme = initReadme
                )
                _uiState.update { it.copy(isCreating = false, showCreateDialog = false) }
                onCreated(workspace)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = e.localizedMessage ?: "Failed to create project"
                    )
                }
            }
        }
    }

    fun cloneWorkspace(
        remoteUrl: String,
        displayName: String,
        branch: String = "main",
        token: String? = null,
        onCloned: (Workspace) -> Unit = {}
    ) {
        val profileId = _uiState.value.activeProfileId
        if (profileId.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please configure and select a connection profile first.") }
            return
        }

        val cleanUrl = remoteUrl.trim()
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Repository URL cannot be empty.") }
            return
        }

        val name = displayName.trim().ifBlank {
            cleanUrl.substringAfterLast("/").removeSuffix(".git").ifBlank { "Cloned Repo" }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCloning = true, errorMessage = null) }
            try {
                val workspace = workspaceRepository.createWorkspace(
                    profileId = profileId,
                    displayName = name,
                    sourceType = WorkspaceSourceType.CLONED,
                    remoteUrl = cleanUrl,
                    defaultBranch = branch.trim().ifBlank { "main" },
                    token = token?.trim()?.ifBlank { null },
                    initReadme = false
                )
                _uiState.update { it.copy(isCloning = false, showCloneSheet = false) }
                onCloned(workspace)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        errorMessage = e.localizedMessage ?: "Failed to clone repository"
                    )
                }
            }
        }
    }

    fun toggleArchive(workspaceId: String, currentArchived: Boolean) {
        viewModelScope.launch {
            try {
                workspaceRepository.setArchived(workspaceId, !currentArchived)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to update archive status") }
            }
        }
    }

    fun deleteWorkspace(workspaceId: String, deleteFiles: Boolean = true) {
        viewModelScope.launch {
            try {
                workspaceRepository.deleteWorkspace(workspaceId, deleteFiles = deleteFiles)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Failed to delete project") }
            }
        }
    }

    fun touchWorkspace(workspaceId: String) {
        viewModelScope.launch {
            workspaceRepository.touchWorkspace(workspaceId)
        }
    }
}
