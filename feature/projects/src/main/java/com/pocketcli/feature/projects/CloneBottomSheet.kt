package com.pocketcli.feature.projects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.MonospaceCodeStyle
import com.pocketcli.core.ui.theme.ToolSuccessColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneBottomSheet(
    isCloning: Boolean,
    cloneStage: CloneStage,
    cloneLog: List<String>,
    onDismiss: () -> Unit,
    onClone: (url: String, name: String, branch: String, token: String?) -> Unit,
    modifier: Modifier = Modifier,
    onResetStage: () -> Unit = {}
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var isShallow by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!isCloning) onDismiss() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Клонировать Git-репозиторий",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (isCloning || cloneStage != CloneStage.IDLE) {
                // Step 4: Clone Progress Card
                CloneProgressCard(
                    stage = cloneStage,
                    logs = cloneLog,
                    showLog = showLog,
                    onToggleShowLog = { showLog = !showLog },
                    onRetry = {
                        onClone(url, name, branch, if (token.isBlank()) null else token)
                    },
                    onEditInputs = {
                        onResetStage()
                    }
                )
            } else {
                // Steps 1-3 Form
                OutlinedTextField(
                    value = url,
                    onValueChange = { newUrl ->
                        url = newUrl
                        val inferred = newUrl.trim()
                            .substringAfterLast("/")
                            .removeSuffix(".git")
                        if (inferred.isNotBlank() && name.isBlank()) {
                            name = inferred
                        }
                    },
                    label = { Text("HTTPS URL репозитория") },
                    placeholder = { Text("https://github.com/owner/repo.git") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название проекта") },
                    placeholder = { Text("my-project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Ветка") },
                    placeholder = { Text("main") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shallow clone (--depth 1)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Экономит мобильный трафик и место на диске",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isShallow,
                        onCheckedChange = { isShallow = it }
                    )
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal Access Token (для приватных репо)") },
                    placeholder = { Text("ghp_... (опционально)") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showToken) "Скрыть токен" else "Показать токен"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onClone(
                            url.trim(),
                            name.trim(),
                            branch.trim().ifBlank { "main" },
                            token.trim().ifBlank { null }
                        )
                    },
                    enabled = url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Клонировать")
                }
            }
        }
    }
}

@Composable
fun CloneProgressCard(
    stage: CloneStage,
    logs: List<String>,
    showLog: Boolean,
    onToggleShowLog: () -> Unit,
    onRetry: () -> Unit = {},
    onEditInputs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (stage == CloneStage.ERROR) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (stage == CloneStage.ERROR) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Ошибка",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Не удалось клонировать репозиторий",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = logs.lastOrNull { it.startsWith("Ошибка:") } ?: "Произошла ошибка при подготовке репозитория.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEditInputs,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Изменить данные")
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Повторить")
                    }
                }
            } else {
                Text(
                    text = "Подготовка репозитория",
                    style = MaterialTheme.typography.titleMedium
                )

                val stages = listOf(
                    Pair(CloneStage.CHECK_URL, "Проверка URL"),
                    Pair(CloneStage.CONNECTING, "Подключение"),
                    Pair(CloneStage.FETCHING_OBJECTS, "Получение объектов"),
                    Pair(CloneStage.UNPACKING, "Распаковка"),
                    Pair(CloneStage.VERIFYING_GIT, "Проверка Git"),
                    Pair(CloneStage.READY, "Готово")
                )

                val currentStageIndex = stages.indexOfFirst { it.first == stage }

                for ((index, item) in stages.withIndex()) {
                    val (stageEnum, label) = item
                    val isCompleted = currentStageIndex > index || stage == CloneStage.READY
                    val isCurrent = currentStageIndex == index && stage != CloneStage.READY

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ToolSuccessColor,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (isCurrent) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }

                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCompleted || isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TextButton(
                onClick = onToggleShowLog,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (showLog) "Скрыть журнал" else "Показать журнал")
            }

            AnimatedVisibility(visible = showLog && logs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        for (log in logs) {
                            Text(
                                text = log,
                                style = MonospaceCodeStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }
        }
    }
}
