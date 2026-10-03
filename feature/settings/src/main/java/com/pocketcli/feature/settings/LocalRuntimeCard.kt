package com.pocketcli.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketcli.runtime.local.installer.InstallerState
import com.pocketcli.runtime.local.supervisor.LocalRuntimeState

@Composable
fun LocalRuntimeCard(
    installerState: InstallerState,
    supervisorState: LocalRuntimeState,
    isLocalActive: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onActivate: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenKeys: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (isLocalActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Локальный агент (PRoot)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (isLocalActive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Активен") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status indication
            when (installerState) {
                is InstallerState.NotInstalled -> {
                    Text(
                        text = "Рантайм не установлен. Нажмите «Установить» для автономной работы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Установить рантайм (~60 МБ)")
                    }
                }

                is InstallerState.CheckingPrerequisites -> {
                    StatusRow(text = "Проверка диска и архитектуры...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                is InstallerState.Downloading -> {
                    val mbDownloaded = installerState.downloadedBytes / (1024 * 1024)
                    val mbTotal = installerState.totalBytes / (1024 * 1024)
                    StatusRow(text = "Скачивание rootfs: $mbDownloaded МБ / $mbTotal МБ (${(installerState.progress * 100).toInt()}%)")
                    LinearProgressIndicator(
                        progress = { installerState.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                is InstallerState.Verifying -> {
                    StatusRow(text = "Проверка контрольной суммы SHA-256...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                is InstallerState.Extracting -> {
                    StatusRow(text = "Распаковка rootfs...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                is InstallerState.Configuring -> {
                    StatusRow(text = "Настройка окружения и DNS...")
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                is InstallerState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ошибка установки: ${installerState.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onInstall) {
                        Text("Повторить установку")
                    }
                }

                is InstallerState.Ready -> {
                    // Ready: show supervisor state
                    when (supervisorState) {
                        is LocalRuntimeState.Stopped -> {
                            StatusRow(text = "Статус: Остановлен", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Запустить")
                                }
                                OutlinedButton(onClick = onUninstall) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        is LocalRuntimeState.Starting -> {
                            StatusRow(text = "Запуск сервера OpenCode...", color = MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }

                        is LocalRuntimeState.Running -> {
                            StatusRow(
                                text = "Запущен: порт ${supervisorState.port} (PID: ${supervisorState.pid ?: "N/A"})",
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Стоп")
                                }
                                OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Рестарт")
                                }
                                if (!isLocalActive) {
                                    Button(onClick = onActivate) {
                                        Text("Выбрать")
                                    }
                                }
                            }
                        }

                        is LocalRuntimeState.Stopping -> {
                            StatusRow(text = "Остановка сервера...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }

                        is LocalRuntimeState.Failed -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Сбой: ${supervisorState.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onStart) {
                                    Text("Перезапустить")
                                }
                                OutlinedButton(onClick = onUninstall) {
                                    Text("Сбросить")
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action row: Logs & Provider Keys
            if (installerState is InstallerState.Ready) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onOpenLogs,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Логи")
                    }
                    OutlinedButton(
                        onClick = onOpenKeys,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("API-ключи")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}
