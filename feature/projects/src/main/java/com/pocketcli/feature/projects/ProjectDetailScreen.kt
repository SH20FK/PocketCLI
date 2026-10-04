package com.pocketcli.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.core.ui.components.AgentActivityState
import com.pocketcli.core.ui.components.DiffSummaryCard
import com.pocketcli.core.ui.components.FileDiffViewer
import com.pocketcli.core.ui.components.PocketButtonGroup
import com.pocketcli.core.ui.components.PocketStatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    workspaceItem: WorkspaceWithDetails,
    onNavigateBack: () -> Unit,
    onStartChat: (workspaceId: String) -> Unit,
    onDeleteProject: (workspaceId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val workspace = workspaceItem.workspace
    val git = workspaceItem.gitStatus
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedDiffFile by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val tabs = listOf("Обзор", "Файлы", "Git", "Terminal")

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(top = 4.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = workspace.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = workspace.localPath.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    PocketStatusPill(state = AgentActivityState.READY, customText = "Локально")
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Удалить проект") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteProject(workspace.id)
                                    onNavigateBack()
                                }
                            )
                        }
                    }
                }
            )
        }
    },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Новый чат") },
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                onClick = { onStartChat(workspace.id) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Connected ButtonGroup navigation
            PocketButtonGroup(
                options = tabs,
                selectedIndex = selectedTabIndex,
                onSelectIndex = { selectedTabIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // Tab Content
            when (selectedTabIndex) {
                0 -> OverviewTab(
                    workspaceItem = workspaceItem,
                    onStartChat = { onStartChat(workspace.id) }
                )
                1 -> FilesTab(workspaceDirectory = File(workspace.localPath))
                2 -> GitTab(
                    workspaceItem = workspaceItem,
                    selectedDiff = selectedDiffFile,
                    onSelectDiff = { selectedDiffFile = it },
                    onCloseDiff = { selectedDiffFile = null }
                )
                3 -> TerminalTab(workspaceDirectory = workspace.localPath)
            }
        }
    }
}

@Composable
fun OverviewTab(
    workspaceItem: WorkspaceWithDetails,
    onStartChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workspace = workspaceItem.workspace
    val git = workspaceItem.gitStatus

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Quick Action Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Продолжить работу с агентом",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Запустите новую задачу в контексте репозитория",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onStartChat) {
                        Text("Чат")
                    }
                }
            }
        }

        // Git Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Состояние Git", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val branchName = git.branch ?: "не инициализирован"
                    Text(
                        text = "Ветка: $branchName",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (git.isDirty) "Есть незакоммиченные изменения" else "Рабочая копия чистая",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Workspace Metadata Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Информация о рабочем пространстве", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Путь: ${workspace.localPath}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!workspace.remoteUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remote: ${workspace.remoteUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(
    workspaceDirectory: File,
    modifier: Modifier = Modifier
) {
    val files by produceState(initialValue = emptyList<File>(), workspaceDirectory) {
        value = withContext(Dispatchers.IO) {
            if (workspaceDirectory.exists() && workspaceDirectory.isDirectory) {
                workspaceDirectory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
            } else {
                emptyList()
            }
        }
    }

    if (files.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text(
                text = "В этой директории пока нет файлов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(files, key = { it.absolutePath }) { file ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GitTab(
    workspaceItem: WorkspaceWithDetails,
    selectedDiff: String?,
    onSelectDiff: (String) -> Unit,
    onCloseDiff: () -> Unit,
    modifier: Modifier = Modifier
) {
    val git = workspaceItem.gitStatus

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = "Git репозиторий",
            style = MaterialTheme.typography.titleMedium
        )

        if (!git.isGitRepo) {
            Text(
                text = "Директория проекта не является инициализированным Git-репозиторием.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ветка: ${git.currentBranch ?: "main"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        if (git.isDirty) {
                            Text(
                                text = "Есть изменения",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "Чисто",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Детальный просмотр Git-диффов и коммитов находится в разработке (Этап 3). Для просмотра изменений вы можете запросить у агента команду `git status` или `git diff` в чате.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalTab(
    workspaceDirectory: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Рабочая директория: $workspaceDirectory",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Интерактивный терминал (PTY) находится в разработке (Этап 3).",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Команды выполняются автономно через инструменты ИИ-агента (bash) в сессиях чата. Логи работы PRoot сервера доступны в Настройках -> Логи рантайма.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
