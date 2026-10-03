package com.pocketcli.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.data.local.repository.WorkspaceRepository
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProjectFilter {
    ALL,
    LOCAL,
    REMOTE
}

enum class ProjectSort {
    RECENT,
    NAME,
    ACTIVITY
}

enum class CloneStage {
    IDLE,
    CHECK_URL,
    CONNECTING,
    FETCHING_OBJECTS,
    UNPACKING,
    VERIFYING_GIT,
    READY,
    ERROR
}

data class ProjectsUiState(
    val activeProfileId: String = "",
    val activeProfileName: String = "",
    val workspaces: List<WorkspaceWithDetails> = emptyList(),
    val archivedWorkspaces: List<Workspace> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val filter: ProjectFilter = ProjectFilter.ALL,
    val sort: ProjectSort = ProjectSort.RECENT,
    val filterAttention: Boolean = false,
    val showSortSheet: Boolean = false,
    val showArchived: Boolean = false,
    val isLoading: Boolean = false,
    val isCloning: Boolean = false,
    val cloneStage: CloneStage = CloneStage.IDLE,
    val cloneLog: List<String> = emptyList(),
    val isCreating: Boolean = false,
    val isImporting: Boolean = false,
    val showCloneSheet: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showImportDialog: Boolean = false,
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

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active, searchQuery = if (!active) "" else it.searchQuery) }
    }

    fun setFilter(filter: ProjectFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun setSort(sort: ProjectSort) {
        _uiState.update { it.copy(sort = sort, showSortSheet = false) }
    }

    fun toggleFilterAttention() {
        _uiState.update { it.copy(filterAttention = !it.filterAttention) }
    }

    fun openSortSheet() {
        _uiState.update { it.copy(showSortSheet = true) }
    }

    fun dismissSortSheet() {
        _uiState.update { it.copy(showSortSheet = false) }
    }

    fun openCloneSheet() {
        _uiState.update { it.copy(showCloneSheet = true, cloneStage = CloneStage.IDLE, errorMessage = null) }
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

    fun openImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, errorMessage = null) }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false, errorMessage = null) }
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
            _uiState.update { it.copy(errorMessage = "Сначала выберите или настройте профиль подключения.") }
            return
        }

        val name = displayName.trim().ifBlank { "Проект" }

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
                        errorMessage = e.localizedMessage ?: "Не удалось создать проект"
                    )
                }
            }
        }
    }

    fun importWorkspace(
        displayName: String,
        onImported: (Workspace) -> Unit = {}
    ) {
        val profileId = _uiState.value.activeProfileId
        if (profileId.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Сначала выберите или настройте профиль подключения.") }
            return
        }

        val name = displayName.trim().ifBlank { "Импортированный проект" }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            try {
                val workspace = workspaceRepository.createWorkspace(
                    profileId = profileId,
                    displayName = name,
                    sourceType = WorkspaceSourceType.IMPORTED,
                    initReadme = false
                )
                _uiState.update { it.copy(isImporting = false, showImportDialog = false) }
                onImported(workspace)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = e.localizedMessage ?: "Не удалось импортировать проект"
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
            _uiState.update { it.copy(errorMessage = "Сначала выберите или настройте профиль подключения.") }
            return
        }

        val cleanUrl = remoteUrl.trim()
        if (cleanUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "URL репозитория не может быть пустым.") }
            return
        }

        val name = displayName.trim().ifBlank {
            cleanUrl.substringAfterLast("/").removeSuffix(".git").ifBlank { "Клонированный проект" }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCloning = true,
                    cloneStage = CloneStage.CHECK_URL,
                    cloneLog = listOf("Проверка URL: $cleanUrl"),
                    errorMessage = null
                )
            }

            try {
                delay(300)
                _uiState.update {
                    it.copy(
                        cloneStage = CloneStage.CONNECTING,
                        cloneLog = it.cloneLog + "Подключение к удалённому репозиторию..."
                    )
                }

                delay(400)
                _uiState.update {
                    it.copy(
                        cloneStage = CloneStage.FETCHING_OBJECTS,
                        cloneLog = it.cloneLog + "Получение объектов (ветка $branch)..."
                    )
                }

                val workspace = workspaceRepository.createWorkspace(
                    profileId = profileId,
                    displayName = name,
                    sourceType = WorkspaceSourceType.CLONED,
                    remoteUrl = cleanUrl,
                    defaultBranch = branch,
                    token = token,
                    initReadme = false
                )

                delay(300)
                _uiState.update {
                    it.copy(
                        cloneStage = CloneStage.UNPACKING,
                        cloneLog = it.cloneLog + "Распаковка workspace..."
                    )
                }

                delay(200)
                _uiState.update {
                    it.copy(
                        cloneStage = CloneStage.VERIFYING_GIT,
                        cloneLog = it.cloneLog + "Проверка Git окружения..."
                    )
                }

                delay(200)
                _uiState.update {
                    it.copy(
                        cloneStage = CloneStage.READY,
                        cloneLog = it.cloneLog + "Готово! Проект успешно подготовлен."
                    )
                }

                delay(300)
                _uiState.update { it.copy(isCloning = false, showCloneSheet = false) }
                onCloned(workspace)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCloning = false,
                        cloneStage = CloneStage.ERROR,
                        cloneLog = it.cloneLog + "Ошибка: ${e.localizedMessage}",
                        errorMessage = e.localizedMessage ?: "Не удалось клонировать репозиторий"
                    )
                }
            }
        }
    }

    fun resetCloneStage() {
        _uiState.update { it.copy(cloneStage = CloneStage.IDLE, errorMessage = null) }
    }

    fun toggleArchiveWorkspace(id: String, currentArchived: Boolean) {
        viewModelScope.launch {
            try {
                workspaceRepository.setArchived(id, !currentArchived)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun deleteWorkspace(id: String, deleteFiles: Boolean = true) {
        viewModelScope.launch {
            try {
                workspaceRepository.deleteWorkspace(id, deleteFiles = deleteFiles)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
