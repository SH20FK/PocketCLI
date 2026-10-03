package com.pocketcli.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketcli.core.security.AntigravityAuthState
import com.pocketcli.core.security.AntigravityAuthType
import com.pocketcli.core.security.DeviceAuthCode

@Composable
fun ClaudeCodeConfigDialog(
    currentApiKey: String,
    onSaveKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyDraft by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("claude-3-7-sonnet") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Настройка Claude Code")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Anthropic Claude Code агент подключен через протокол ACP (Agent Client Protocol). Для работы требуется API-ключ Anthropic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Протокол: ACP JSON-RPC 2.0 (stdio)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Адаптер Zed / локальный процесс PRoot с автоматической маршрутизацией команд и стримингом мыслей.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("Anthropic API Key (sk-ant-...)") },
                    placeholder = { Text("ANTHROPIC_API_KEY") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Скрыть" else "Показать"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Модель по умолчанию",
                    style = MaterialTheme.typography.titleSmall
                )

                val models = listOf(
                    "claude-3-7-sonnet" to "Claude 3.7 Sonnet (Hybrid)",
                    "claude-3-5-sonnet" to "Claude 3.5 Sonnet",
                    "claude-3-5-haiku" to "Claude 3.5 Haiku"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((modelId, modelName) in models) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedModel == modelId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            onClick = { selectedModel = modelId },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedModel == modelId,
                                    onClick = { selectedModel = modelId }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = modelId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveKey(apiKeyDraft)
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AntigravityConfigDialog(
    authState: AntigravityAuthState,
    currentApiKey: String,
    onStartDeviceAuth: ((Result<DeviceAuthCode>) -> Unit) -> Unit,
    onPollDeviceToken: (String, (Result<Boolean>) -> Unit) -> Unit,
    onSaveKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyDraft by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember(authState.selectedModel) { mutableStateOf(authState.selectedModel) }

    // Device Flow UI state
    var isStartingAuth by remember { mutableStateOf(false) }
    var isPollingAuth by remember { mutableStateOf(false) }
    var deviceCodeData by remember { mutableStateOf<DeviceAuthCode?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text("Google Antigravity & Gemini")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Google DeepMind Advanced Agentic Coding с контекстным окном до 2M токенов и потоковым выводом рассуждений.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Google OAuth Authentication Card
                if (authState.isAuthenticated) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (authState.authType == AntigravityAuthType.GOOGLE_OAUTH) "Google OAuth подключен" else "API-ключ активен",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            val email = authState.userEmail
                            if (!email.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onLogout,
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Выйти из аккаунта", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    // Not authenticated: Google OAuth login button or Device Code prompt
                    if (deviceCodeData == null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Авторизация Google OAuth",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Войдите в Google аккаунт для доступа к моделям Gemini 2.5 Pro и Gemini Flash через Antigravity CLI.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (authError != null) {
                                    Text(
                                        text = authError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Button(
                                    onClick = {
                                        isStartingAuth = true
                                        authError = null
                                        onStartDeviceAuth { result ->
                                            isStartingAuth = false
                                            result.onSuccess { code ->
                                                deviceCodeData = code
                                                isPollingAuth = true
                                                onPollDeviceToken(code.deviceCode) { pollResult ->
                                                    isPollingAuth = false
                                                    pollResult.onFailure { err ->
                                                        authError = err.message
                                                    }
                                                }
                                            }.onFailure { err ->
                                                authError = err.message
                                            }
                                        }
                                    },
                                    enabled = !isStartingAuth,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isStartingAuth) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("Войти через Google")
                                }
                            }
                        }
                    } else {
                        // Display Device Code card
                        val code = deviceCodeData!!
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Код подтверждения устройства",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = code.userCode,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(code.userCode))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Скопировать")
                                    }
                                    Button(
                                        onClick = {
                                            uriHandler.openUri(code.verificationUrl)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Открыть сайт")
                                    }
                                }

                                if (isPollingAuth) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Ожидание подтверждения в Google...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                if (authError != null) {
                                    Text(
                                        text = authError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Default Model Selection
                Text(
                    text = "Модель по умолчанию",
                    style = MaterialTheme.typography.titleSmall
                )

                val models = listOf(
                    "gemini-2.5-pro" to "Gemini 2.5 Pro (DeepMind SOTA)",
                    "gemini-2.5-flash" to "Gemini 2.5 Flash (Быстрая генерация)",
                    "gemini-2.0-flash-thinking" to "Gemini 2.0 Flash Thinking (Мысли вслух)",
                    "gemini-2.0-flash" to "Gemini 2.0 Flash (Общие задачи)"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((modelId, modelName) in models) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedModel == modelId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            onClick = {
                                selectedModel = modelId
                                onSelectModel(modelId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedModel == modelId,
                                    onClick = {
                                        selectedModel = modelId
                                        onSelectModel(modelId)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = modelId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Fallback manual API key input
                Text(
                    text = "Или Google Gemini API Key вручную",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("GEMINI_API_KEY (AIzaSy...)") },
                    placeholder = { Text("Необязательно при наличии OAuth") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Скрыть" else "Показать"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveKey(apiKeyDraft)
                    onDismiss()
                }
            ) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun AntigravityConfigDialog(
    currentApiKey: String,
    onSaveKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AntigravityConfigDialog(
        authState = AntigravityAuthState(isAuthenticated = currentApiKey.isNotBlank(), hasApiKey = currentApiKey.isNotBlank()),
        currentApiKey = currentApiKey,
        onStartDeviceAuth = {},
        onPollDeviceToken = { _, _ -> },
        onSaveKey = onSaveKey,
        onSelectModel = {},
        onLogout = { onSaveKey("") },
        onDismiss = onDismiss
    )
}
