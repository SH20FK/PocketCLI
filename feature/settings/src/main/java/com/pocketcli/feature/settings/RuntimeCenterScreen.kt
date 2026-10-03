package com.pocketcli.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.components.AgentActivityState
import com.pocketcli.core.ui.components.PocketStatusPill
import com.pocketcli.core.ui.theme.ToolSuccessColor
import com.pocketcli.runtime.local.supervisor.SupervisorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeCenterScreen(
    supervisorState: SupervisorState,
    onNavigateBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenKeys: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = supervisorState is SupervisorState.Running

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                title = { Text("Runtime Center") },
                actions = {
                    PocketStatusPill(
                        state = if (isRunning) AgentActivityState.READY else AgentActivityState.OFFLINE,
                        customText = if (isRunning) "Работает" else "Остановлен"
                    )
                }
            )
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
            // Hero Status Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = if (isRunning) "Локальный рантайм активен" else "Рантайм остановлен",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                if (supervisorState is SupervisorState.Running) {
                                    Text(
                                        text = "Порт: ${supervisorState.port} · Uptime: ${supervisorState.uptimeSeconds}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = if (isRunning) ToolSuccessColor else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRunning) {
                                OutlinedButton(
                                    onClick = onStop,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Остановить")
                                }
                                Button(
                                    onClick = onRestart,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Рестарт")
                                }
                            } else {
                                Button(
                                    onClick = onStart,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Запустить рантайм")
                                }
                            }
                        }
                    }
                }
            }

            // Quick Tools Card (Logs, Keys)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onOpenLogs,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Журнал логов")
                    }

                    FilledTonalButton(
                        onClick = onOpenKeys,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("API Ключи")
                    }
                }
            }

            // Runtime Components & Specifications
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Компоненты окружения",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("OpenCode Binary", style = MaterialTheme.typography.bodyMedium)
                            Text("1.2.27 (musl-arm64)", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Базовая ОС", style = MaterialTheme.typography.bodyMedium)
                            Text("Alpine Linux 3.21.3", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Изоляция", style = MaterialTheme.typography.bodyMedium)
                            Text("PRoot v5.3.0 (no root)", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Хранилище", style = MaterialTheme.typography.bodyMedium)
                            Text("app-private sandbox", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
