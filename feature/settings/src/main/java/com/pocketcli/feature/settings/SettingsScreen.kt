package com.pocketcli.feature.settings

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketcli.core.ui.components.AgentActivityState
import com.pocketcli.core.ui.components.PocketDialog
import com.pocketcli.core.ui.components.PocketStatusPill
import com.pocketcli.core.ui.components.PocketTwoRowsTopAppBar
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.data.local.db.ConnectionProfileEntity
import com.pocketcli.runtime.local.supervisor.LocalRuntimeSupervisor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    runtimeViewModel: LocalRuntimeViewModel = hiltViewModel(),
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToRuntimeCenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val runtimeUiState by runtimeViewModel.uiState.collectAsState()
    val installerState by runtimeViewModel.installerState.collectAsState()
    val supervisorState by runtimeViewModel.supervisorState.collectAsState()
    val logs by runtimeViewModel.logs.collectAsState()
    val providerKeys by runtimeViewModel.providerKeys.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(runtimeUiState.errorMessage) {
        runtimeUiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            PocketTwoRowsTopAppBar(
                title = "Настройки",
                subtitle = "Управление рантаймом, серверами и ключами моделей",
                statusPill = {
                    PocketStatusPill(state = AgentActivityState.READY, customText = "Локально")
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddDialog() }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить удаленный сервер")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Section 1: Local PRoot Runtime
            item {
                LocalRuntimeCard(
                    installerState = installerState,
                    supervisorState = supervisorState,
                    isLocalActive = uiState.activeProfileId == LocalRuntimeSupervisor.LOCAL_PROFILE_ID,
                    onInstall = { runtimeViewModel.install() },
                    onUninstall = { runtimeViewModel.uninstall() },
                    onStart = { runtimeViewModel.startServer() },
                    onStop = { runtimeViewModel.stopServer() },
                    onRestart = { runtimeViewModel.restartServer() },
                    onActivate = { runtimeViewModel.activateLocalProfile() },
                    onOpenLogs = { runtimeViewModel.openLogsDialog() },
                    onOpenKeys = { runtimeViewModel.openProviderKeysDialog() }
                )
            }

            // Section 2: OTA App Updates
            item {
                Surface(
                    shape = PocketShapes.container,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PocketShapes.container)
                        .clickable { onNavigateToUpdate() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(PocketSpacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Обновление приложения",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Проверка новых релизов через GitHub Releases",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 3: Supported Agents
            item {
                Text(
                    text = "Поддерживаемые агенты и протоколы",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                AgentCardsList(
                    onConfigureOpenCode = { runtimeViewModel.openProviderKeysDialog() }
                )
            }

            // Section 4: Remote OpenCode Servers
            item {
                Text(
                    text = "Удалённые серверы OpenCode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val remoteProfiles = uiState.profiles.filter { it.id != LocalRuntimeSupervisor.LOCAL_PROFILE_ID }
            if (remoteProfiles.isEmpty()) {
                item {
                    Text(
                        text = "Нет добавленных удаленных серверов. Нажмите +, чтобы подключиться к компьютеру или VPS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                items(remoteProfiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == uiState.activeProfileId
                    ProfileCard(
                        profile = profile,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectActiveProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile.id) }
                    )
                }
            }
        }

        // Add Profile Dialog
        if (uiState.showAddDialog) {
            AddProfileDialog(
                uiState = uiState,
                onNameChange = { viewModel.onDraftNameChange(it) },
                onUrlChange = { viewModel.onDraftUrlChange(it) },
                onUsernameChange = { viewModel.onDraftUsernameChange(it) },
                onPasswordChange = { viewModel.onDraftPasswordChange(it) },
                onCleartextToggle = { viewModel.onDraftCleartextToggle(it) },
                onTest = { viewModel.testConnection() },
                onSave = { viewModel.saveProfile() },
                onDismiss = { viewModel.dismissAddDialog() }
            )
        }

        // Log Viewer Dialog
        if (runtimeUiState.showLogsDialog) {
            LogViewerDialog(
                logs = logs,
                filterQuery = runtimeUiState.logFilterQuery,
                onFilterChange = { runtimeViewModel.updateLogFilter(it) },
                onClearLogs = { runtimeViewModel.clearLogs() },
                onDismiss = { runtimeViewModel.dismissLogsDialog() }
            )
        }

        // Provider API Keys Dialog
        if (runtimeUiState.showProviderKeysDialog) {
            ProviderKeysDialog(
                currentKeys = providerKeys,
                onSaveKey = { prov, key -> runtimeViewModel.saveProviderKey(prov, key) },
                onDismiss = { runtimeViewModel.dismissProviderKeysDialog() }
            )
        }
    }
}

@Composable
fun ProfileCard(
    profile: ConnectionProfileEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Активен",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = profile.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить профиль",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddProfileDialog(
    uiState: SettingsUiState,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCleartextToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    PocketDialog(
        onDismissRequest = onDismiss,
        title = "Новый сервер",
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = uiState.draftName.isNotBlank() && uiState.draftUrl.isNotBlank() && !uiState.isTestingConnection
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = uiState.draftName,
                onValueChange = onNameChange,
                label = { Text("Название") },
                placeholder = { Text("Home PC") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.draftUrl,
                onValueChange = onUrlChange,
                label = { Text("URL сервера") },
                placeholder = { Text("http://192.168.1.50:4096") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.draftUsername,
                onValueChange = onUsernameChange,
                label = { Text("Логин") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.draftPassword,
                onValueChange = onPasswordChange,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Разрешить HTTP (Cleartext)", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = uiState.draftAllowCleartext,
                    onCheckedChange = onCleartextToggle
                )
            }

            Button(
                onClick = onTest,
                enabled = uiState.draftUrl.isNotBlank() && !uiState.isTestingConnection,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Проверить подключение")
            }

            uiState.testConnectionStatus?.let { res ->
                Text(
                    text = res,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (res.startsWith("Success") || res.startsWith("Успешно")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
