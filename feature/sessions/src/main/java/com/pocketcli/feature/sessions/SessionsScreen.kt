package com.pocketcli.feature.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.AgentType
import com.pocketcli.core.model.Session
import com.pocketcli.core.ui.components.AgentActivityState
import com.pocketcli.core.ui.components.PocketAppBarWithSearch
import com.pocketcli.core.ui.components.PocketDialog
import com.pocketcli.core.ui.components.PocketStatusPill
import com.pocketcli.core.ui.components.ProjectPickerSheet
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import kotlinx.coroutines.launch
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val workspaceMap = remember(uiState.workspaces) {
        uiState.workspaces.associateBy({ it.id }, { it.displayName })
    }

    val filteredSessions = remember(uiState.sessions, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) uiState.sessions
        else uiState.sessions.filter {
            it.title.contains(uiState.searchQuery, ignoreCase = true) ||
            (it.workspaceId?.let { wsId -> workspaceMap[wsId]?.contains(uiState.searchQuery, ignoreCase = true) } == true)
        }
    }

    val activeSessions = remember(filteredSessions, uiState.activeSessionIds) {
        filteredSessions.filter { uiState.activeSessionIds.contains(it.id) }
    }

    val recentSessions = remember(filteredSessions, uiState.activeSessionIds) {
        filteredSessions.filter { !uiState.activeSessionIds.contains(it.id) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PocketAppBarWithSearch(
                title = "Чаты",
                subtitle = if (uiState.activeProfileName.isNotEmpty()) "Профиль: ${uiState.activeProfileName}" else null,
                statusPill = {
                    PocketStatusPill(state = AgentActivityState.READY, customText = "Локально")
                },
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onToggleSearch = { viewModel.toggleSearch(it) },
                actions = {
                    IconButton(onClick = { viewModel.syncSessions(uiState.activeProfileId) }) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = "Синхронизировать")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Новый чат") },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = "Новый чат") },
                onClick = { viewModel.openCreateDialog() }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (filteredSessions.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Text(
                            text = "Начните первый чат",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (uiState.activeProfileId.isEmpty()) {
                                "Нет активного профиля OpenCode.\nПерейдите в Настройки для настройки рантайма."
                            } else {
                                "Сессий пока нет.\nНажмите «Новый чат» для постановки задачи агенту."
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (uiState.activeProfileId.isNotEmpty()) {
                            Button(onClick = { viewModel.openCreateDialog() }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Новый чат")
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
                    // Section 1: Продолжаются сейчас (Активные)
                    if (activeSessions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Продолжаются сейчас",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(activeSessions, key = { "active_${it.id}" }) { session ->
                            val wsName = session.workspaceId?.let { workspaceMap[it] }
                            SessionItemCard(
                                session = session,
                                workspaceName = wsName,
                                onClick = { onSessionClick(session.id, session.profileId) },
                                onDelete = {
                                    viewModel.deleteSession(session.id)
                                    coroutineScope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = "Сессия удалена",
                                            actionLabel = "Отменить",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.undoDeleteSession()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Section 2: Недавние сессии
                    if (recentSessions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Недавние диалоги",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(recentSessions, key = { "recent_${it.id}" }) { session ->
                            val wsName = session.workspaceId?.let { workspaceMap[it] }
                            SessionItemCard(
                                session = session,
                                workspaceName = wsName,
                                onClick = { onSessionClick(session.id, session.profileId) },
                                onDelete = {
                                    viewModel.deleteSession(session.id)
                                    coroutineScope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = "Сессия удалена",
                                            actionLabel = "Отменить",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.undoDeleteSession()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Create Session Dialog
        if (uiState.showCreateDialog) {
            val selectedWs = uiState.workspaces.find { it.id == uiState.selectedWorkspaceId }

            PocketDialog(
                onDismissRequest = { viewModel.dismissCreateDialog() },
                title = "Новый диалог",
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.createSession { sessionId ->
                                onSessionClick(sessionId, uiState.activeProfileId)
                            }
                        },
                        enabled = !uiState.isCreatingSession
                    ) {
                        if (uiState.isCreatingSession) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Создать")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissCreateDialog() }) {
                        Text("Отмена")
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = uiState.newSessionTitle,
                        onValueChange = { viewModel.onNewSessionTitleChange(it) },
                        label = { Text("Название чата") },
                        placeholder = { Text("Например: Исправить авторизацию") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ИИ-агент",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = uiState.selectedAgentType == AgentType.OPENCODE,
                                onClick = { viewModel.selectAgentType(AgentType.OPENCODE) },
                                label = { Text("OpenCode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            FilterChip(
                                selected = uiState.selectedAgentType == AgentType.ANTIGRAVITY,
                                onClick = { viewModel.selectAgentType(AgentType.ANTIGRAVITY) },
                                label = { Text("Antigravity") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            FilterChip(
                                selected = false,
                                onClick = { },
                                enabled = false,
                                label = { Text("Claude (скоро)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.openProjectPicker() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Рабочее пространство",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedWs?.displayName ?: "Без проекта (Глобальный контекст)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать проект"
                            )
                        }
                    }
                }
            }
        }

        // Project Picker Sheet
        if (uiState.showProjectPicker) {
            ProjectPickerSheet(
                workspaces = uiState.workspacesWithDetails,
                selectedWorkspaceId = uiState.selectedWorkspaceId,
                onSelectWorkspace = { wsId: String? -> viewModel.selectWorkspace(wsId) },
                onAddNewProject = { viewModel.dismissProjectPicker() },
                onDismiss = { viewModel.dismissProjectPicker() }
            )
        }
    }
}

@Composable
fun SessionItemCard(
    session: Session,
    workspaceName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(session.updatedAt) { dateFormat.format(Date(session.updatedAt)) }
    var showMenu by remember { mutableStateOf(false) }

    val (agentIcon, agentTint) = when (session.agentType) {
        AgentType.CLAUDE_CODE -> Icons.Default.Bolt to MaterialTheme.colorScheme.primary
        AgentType.ANTIGRAVITY -> Icons.Default.AutoAwesome to MaterialTheme.colorScheme.tertiary
        AgentType.CODEX -> Icons.Default.Code to MaterialTheme.colorScheme.secondary
        AgentType.OPENCODE -> Icons.Default.SmartToy to MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = agentTint.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = agentIcon,
                        contentDescription = null,
                        tint = agentTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = session.title.ifBlank { "Диалог без названия" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val subtitleParts = buildList {
                    if (!workspaceName.isNullOrBlank()) add(workspaceName)
                    add(session.agentType.displayName)
                }

                Text(
                    text = subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Опции",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Удалить чат", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
