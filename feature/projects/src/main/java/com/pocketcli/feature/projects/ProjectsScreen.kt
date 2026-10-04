package com.pocketcli.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.core.ui.components.AgentActivityState
import com.pocketcli.core.ui.components.PocketAppBarWithSearch
import com.pocketcli.core.ui.components.PocketButtonGroup
import com.pocketcli.core.ui.components.PocketStatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onProjectClick: (workspaceId: String) -> Unit = {},
    onStartSessionForProject: (workspaceId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    val filterOptions = listOf("Все", "Локальные", "Git")
    val selectedFilterIndex = when (uiState.filter) {
        ProjectFilter.ALL -> 0
        ProjectFilter.LOCAL -> 1
        ProjectFilter.REMOTE -> 2
    }

    val displayList = remember(uiState.workspaces, uiState.archivedWorkspaces, uiState.showArchived, uiState.filter, uiState.sort, uiState.searchQuery, uiState.filterAttention) {
        val base = if (uiState.showArchived) {
            uiState.archivedWorkspaces.map { ws -> WorkspaceWithDetails(workspace = ws) }
        } else {
            uiState.workspaces
        }

        var filtered = when (uiState.filter) {
            ProjectFilter.ALL -> base
            ProjectFilter.LOCAL -> base.filter { it.workspace.sourceType != WorkspaceSourceType.CLONED }
            ProjectFilter.REMOTE -> base.filter { it.workspace.sourceType == WorkspaceSourceType.CLONED }
        }

        if (uiState.filterAttention) {
            filtered = filtered.filter { it.gitStatus.isDirty }
        }

        if (uiState.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.workspace.displayName.contains(uiState.searchQuery, ignoreCase = true) ||
                it.workspace.localPath.contains(uiState.searchQuery, ignoreCase = true)
            }
        }

        when (uiState.sort) {
            ProjectSort.RECENT -> filtered.sortedByDescending { it.workspace.lastOpenedAt }
            ProjectSort.NAME -> filtered.sortedBy { it.workspace.displayName.lowercase() }
            ProjectSort.ACTIVITY -> filtered.sortedByDescending { it.sessionCount }
        }
    }

    Scaffold(
        topBar = {
            PocketAppBarWithSearch(
                title = if (uiState.showArchived) "Архив проектов" else "Проекты",
                subtitle = if (uiState.activeProfileName.isNotEmpty()) "Профиль: ${uiState.activeProfileName} · ${displayList.size} проектов" else "${displayList.size} проектов",
                statusPill = {
                    PocketStatusPill(state = AgentActivityState.READY, customText = "Локально")
                },
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onToggleSearch = { viewModel.toggleSearch(it) },
                actions = {
                    IconButton(onClick = { viewModel.openSortSheet() }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Сортировка"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleShowArchived() }) {
                        Icon(
                            imageVector = if (uiState.showArchived) Icons.Default.Inventory else Icons.Default.Archive,
                            contentDescription = if (uiState.showArchived) "Активные" else "Архив"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (displayList.isNotEmpty()) {
                AddProjectFabMenu(
                    onCloneClick = { viewModel.openCloneSheet() },
                    onCreateClick = { viewModel.openCreateDialog() },
                    onImportClick = { viewModel.openImportDialog() }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filters bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                PocketButtonGroup(
                    options = filterOptions,
                    selectedIndex = selectedFilterIndex,
                    onSelectIndex = { index ->
                        val filter = when (index) {
                            0 -> ProjectFilter.ALL
                            1 -> ProjectFilter.LOCAL
                            else -> ProjectFilter.REMOTE
                        }
                        viewModel.setFilter(filter)
                    }
                )

                if (uiState.filterAttention) {
                    FilledTonalButton(onClick = { viewModel.toggleFilterAttention() }) {
                        Text("Внимание!", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (displayList.isEmpty()) {
                ProjectsEmptyState(
                    onAddProject = { viewModel.openCloneSheet() }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayList, key = { it.workspace.id }) { item ->
                        ProjectCard(
                            item = item,
                            onClick = { onProjectClick(item.workspace.id) },
                            onStartSession = { onStartSessionForProject(item.workspace.id) },
                            onToggleArchive = {
                                viewModel.toggleArchiveWorkspace(item.workspace.id, item.workspace.archived)
                            },
                            onDelete = { projectToDelete = item.workspace.id }
                        )
                    }
                }
            }
        }

        // Clone Modal Sheet
        if (uiState.showCloneSheet) {
            CloneBottomSheet(
                isCloning = uiState.isCloning,
                cloneStage = uiState.cloneStage,
                cloneLog = uiState.cloneLog,
                onDismiss = { viewModel.dismissCloneSheet() },
                onClone = { url, name, branch, token ->
                    viewModel.cloneWorkspace(url, name, branch, token) { ws ->
                        onProjectClick(ws.id)
                    }
                },
                onResetStage = { viewModel.resetCloneStage() }
            )
        }

        // Create Project Dialog
        if (uiState.showCreateDialog) {
            CreateProjectDialog(
                isCreating = uiState.isCreating,
                onDismiss = { viewModel.dismissCreateDialog() },
                onCreate = { name, initReadme ->
                    viewModel.createWorkspace(name, initReadme) { ws ->
                        onProjectClick(ws.id)
                    }
                }
            )
        }

        // Import Project Dialog
        if (uiState.showImportDialog) {
            ImportWorkspaceDialog(
                isImporting = uiState.isImporting,
                onDismiss = { viewModel.dismissImportDialog() },
                onImport = { name ->
                    viewModel.importWorkspace(name) { ws ->
                        onProjectClick(ws.id)
                    }
                }
            )
        }

        // Sort Bottom Sheet
        if (uiState.showSortSheet) {
            SortBottomSheet(
                currentSort = uiState.sort,
                filterAttention = uiState.filterAttention,
                onSelectSort = { viewModel.setSort(it) },
                onToggleAttention = { viewModel.toggleFilterAttention() },
                onDismiss = { viewModel.dismissSortSheet() }
            )
        }

        // Delete Confirmation Dialog
        projectToDelete?.let { wsId ->
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                title = { Text("Удалить проект?") },
                text = { Text("Это действие удалит проект и его рабочие файлы из PocketCLI.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteWorkspace(wsId, deleteFiles = true)
                            projectToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Удалить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}
