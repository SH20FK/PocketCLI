package com.pocketcli.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketcli.data.local.db.ConnectionProfileEntity
import com.pocketcli.runtime.local.supervisor.LocalRuntimeSupervisor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    runtimeViewModel: LocalRuntimeViewModel = hiltViewModel(),
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
            TopAppBar(
                title = { Text("Настройки и профили") }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Local runtime card at the top
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
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Удалённые серверы OpenCode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val remoteProfiles = uiState.profiles.filter { it.id != LocalRuntimeSupervisor.LOCAL_PROFILE_ID }
            if (remoteProfiles.isEmpty()) {
                item {
                    Text(
                        text = "Нет добавленных удаленных серверов. Нажмите +, чтобы подключиться к компьютеру или VPS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
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
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (uiState.showAddDialog) {
            AddProfileDialog(
                uiState = uiState,
                onNameChange = { viewModel.onDraftNameChange(it) },
                onUrlChange = { viewModel.onDraftUrlChange(it) },
                onUsernameChange = { viewModel.onDraftUsernameChange(it) },
                onPasswordChange = { viewModel.onDraftPasswordChange(it) },
                onCleartextToggle = { viewModel.onDraftCleartextToggle(it) },
                onTestConnection = { viewModel.testConnection() },
                onSave = { viewModel.saveProfile() },
                onDismiss = { viewModel.dismissAddDialog() }
            )
        }

        if (runtimeUiState.showOnboardingDialog) {
            OnboardingDialog(
                onSelectLocal = {
                    runtimeViewModel.dismissOnboarding()
                    runtimeViewModel.install()
                },
                onSelectRemote = {
                    runtimeViewModel.dismissOnboarding()
                    viewModel.openAddDialog()
                },
                onDismiss = { runtimeViewModel.dismissOnboarding() }
            )
        }

        if (runtimeUiState.showLogsDialog) {
            LogViewerDialog(
                logs = logs,
                filterQuery = runtimeUiState.logFilterQuery,
                onFilterChange = { runtimeViewModel.updateLogFilter(it) },
                onClearLogs = { runtimeViewModel.clearLogs() },
                onDismiss = { runtimeViewModel.dismissLogsDialog() }
            )
        }

        if (runtimeUiState.showProviderKeysDialog) {
            ProviderKeysDialog(
                currentKeys = providerKeys,
                onSaveKey = { envVar, value -> runtimeViewModel.saveProviderKey(envVar, value) },
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
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
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
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                if (!isSelected) {
                    TextButton(onClick = onSelect) {
                        Text("Use")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
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
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add OpenCode Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.draftName,
                    onValueChange = onNameChange,
                    label = { Text("Profile Name (e.g. Home PC)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.draftUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Server URL (http://...:4096)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.draftUsername,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.draftPassword,
                    onValueChange = onPasswordChange,
                    label = { Text("Server Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow cleartext HTTP", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.draftAllowCleartext,
                        onCheckedChange = onCleartextToggle
                    )
                }

                if (uiState.draftAllowCleartext) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unencrypted HTTP transmits credentials in plain text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.testConnectionStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !uiState.isTestingConnection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isTestingConnection) "Testing..." else "Test Connection")
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
