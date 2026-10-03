package com.pocketcli.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.core.ui.components.AgentActivityState
import com.pocketcli.core.ui.components.PocketStatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerSheet(
    workspaces: List<WorkspaceWithDetails>,
    selectedWorkspaceId: String?,
    onSelectWorkspace: (String?) -> Unit,
    onAddNewProject: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(workspaces, searchQuery) {
        if (searchQuery.isBlank()) workspaces.take(6)
        else workspaces.filter {
            it.workspace.displayName.contains(searchQuery, ignoreCase = true) ||
            it.workspace.localPath.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Выберите проект",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onAddNewProject) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Создать")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск проектов...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                item {
                    ListItem(
                        headlineContent = { Text("Без проекта (Глобальный контекст)") },
                        supportingContent = { Text("Запуск агента вне конкретного репозитория") },
                        leadingContent = {
                            RadioButton(
                                selected = selectedWorkspaceId == null,
                                onClick = {
                                    onSelectWorkspace(null)
                                    onDismiss()
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectWorkspace(null)
                                onDismiss()
                            }
                    )
                    HorizontalDivider()
                }

                items(filteredList, key = { it.workspace.id }) { item ->
                    val ws = item.workspace
                    val isSelected = ws.id == selectedWorkspaceId

                    ListItem(
                        headlineContent = { Text(ws.displayName) },
                        supportingContent = {
                            Text(
                                text = if (item.gitStatus.isGitRepo) "Ветка ${item.gitStatus.branch ?: "main"}" else ws.localPath.substringAfterLast("/"),
                                maxLines = 1
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectWorkspace(ws.id)
                                    onDismiss()
                                }
                            )
                        },
                        trailingContent = {
                            PocketStatusPill(state = AgentActivityState.READY, customText = "Готов")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectWorkspace(ws.id)
                                onDismiss()
                            }
                    )
                }
            }
        }
    }
}
