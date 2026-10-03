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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (uiState.showArchived) "Archived Projects" else "Projects")
                        if (uiState.activeProfileName.isNotEmpty()) {
                            Text(
                                text = "Profile: ${uiState.activeProfileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleShowArchived() }) {
                        Icon(
                            imageVector = if (uiState.showArchived) Icons.Default.Inventory else Icons.Default.Archive,
                            contentDescription = if (uiState.showArchived) "Show Active" else "Show Archived"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Project")
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Clone Git Repository") },
                        leadingIcon = {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                        },
                        onClick = {
                            showFabMenu = false
                            viewModel.openCloneSheet()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Create Local Project") },
                        leadingIcon = {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        },
                        onClick = {
                            showFabMenu = false
                            viewModel.openCreateDialog()
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val list = if (uiState.showArchived) {
                uiState.archivedWorkspaces.map { ws ->
                    com.pocketcli.core.model.WorkspaceWithDetails(workspace = ws)
                }
            } else {
                uiState.workspaces
            }

            if (list.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        val message = if (uiState.activeProfileId.isEmpty()) {
                            "No active connection profile.\nConfigure your profile in Settings to manage projects."
                        } else if (uiState.showArchived) {
                            "No archived projects."
                        } else {
                            "No projects yet.\nClone a repository or create a local project to get started."
                        }
                        Text(
                            text = message,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (uiState.activeProfileId.isNotEmpty() && !uiState.showArchived) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { viewModel.openCloneSheet() }) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clone Repo")
                                }
                                OutlinedButton(onClick = { viewModel.openCreateDialog() }) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Project")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.workspace.id }) { item ->
                        ProjectCard(
                            item = item,
                            onClick = {
                                viewModel.touchWorkspace(item.workspace.id)
                                onProjectClick(item.workspace.id)
                            },
                            onStartSession = {
                                viewModel.touchWorkspace(item.workspace.id)
                                onStartSessionForProject(item.workspace.id)
                            },
                            onToggleArchive = {
                                viewModel.toggleArchive(item.workspace.id, item.workspace.archived)
                            },
                            onDelete = {
                                projectToDelete = item.workspace.id
                            }
                        )
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }

        if (uiState.showCloneSheet) {
            CloneBottomSheet(
                isCloning = uiState.isCloning,
                onDismiss = { viewModel.dismissCloneSheet() },
                onClone = { url, name, branch, token ->
                    viewModel.cloneWorkspace(url, name, branch, token)
                }
            )
        }

        if (uiState.showCreateDialog) {
            CreateProjectDialog(
                isCreating = uiState.isCreating,
                onDismiss = { viewModel.dismissCreateDialog() },
                onCreate = { name, initReadme ->
                    viewModel.createWorkspace(name, initReadme)
                }
            )
        }

        projectToDelete?.let { wsId ->
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                title = { Text("Delete Project") },
                text = { Text("Are you sure you want to delete this project and its local files? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            viewModel.deleteWorkspace(wsId, deleteFiles = true)
                            projectToDelete = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
