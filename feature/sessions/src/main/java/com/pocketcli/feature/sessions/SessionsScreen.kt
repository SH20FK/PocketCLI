package com.pocketcli.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.Session
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    onSessionClick: (sessionId: String, profileId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val workspaceMap = remember(uiState.workspaces) {
        uiState.workspaces.associateBy({ it.id }, { it.displayName })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agent Sessions")
                        if (uiState.activeProfileName.isNotEmpty()) {
                            Text(
                                text = "Profile: ${uiState.activeProfileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openCreateDialog() }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Session")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.sessions.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    val promptText = if (uiState.activeProfileId.isEmpty()) {
                        "No active server profile.\nGo to Settings to configure your OpenCode server."
                    } else {
                        "No sessions yet for \"${uiState.activeProfileName}\".\nTap + to start a new chat."
                    }
                    Text(
                        text = promptText,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.sessions, key = { it.id }) { session ->
                        val wsName = session.workspaceId?.let { workspaceMap[it] }
                        SessionItemCard(
                            session = session,
                            workspaceName = wsName,
                            onClick = { onSessionClick(session.id, session.profileId) },
                            onDelete = { viewModel.deleteSession(session.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (uiState.showCreateDialog) {
            var expandedDropdown by remember { mutableStateOf(false) }
            val selectedWs = uiState.workspaces.find { it.id == uiState.selectedWorkspaceId }

            AlertDialog(
                onDismissRequest = { viewModel.dismissCreateDialog() },
                title = { Text("New Session") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.newSessionTitle,
                            onValueChange = { viewModel.onNewSessionTitleChange(it) },
                            label = { Text("Session Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.workspaces.isNotEmpty()) {
                            ExposedDropdownMenuBox(
                                expanded = expandedDropdown,
                                onExpandedChange = { expandedDropdown = !expandedDropdown }
                            ) {
                                OutlinedTextField(
                                    value = selectedWs?.displayName ?: "None (Global)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Project / Workspace") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedDropdown,
                                    onDismissRequest = { expandedDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None (Global)") },
                                        onClick = {
                                            viewModel.selectWorkspace(null)
                                            expandedDropdown = false
                                        }
                                    )
                                    for (ws in uiState.workspaces) {
                                        DropdownMenuItem(
                                            text = { Text(ws.displayName) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                viewModel.selectWorkspace(ws.id)
                                                expandedDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        uiState.errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.createSession { onSessionClick(it, uiState.activeProfileId) } },
                        enabled = !uiState.isCreatingSession
                    ) {
                        Text(if (uiState.isCreatingSession) "Creating..." else "Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissCreateDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SessionItemCard(
    session: Session,
    workspaceName: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val formattedTime = remember(session.updatedAt) { dateFormat.format(Date(session.updatedAt)) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Updated: $formattedTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!workspaceName.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = workspaceName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Session",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}
