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
import com.pocketcli.core.security.AntigravityAuthManager
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
    var selectedModel by remember { mutableStateOf("claude-sonnet-4-6") }

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
                    "claude-sonnet-4-6" to "Claude Sonnet 4.6 (Thinking - Флагман 2026)",
                    "claude-opus-4-6-thinking" to "Claude Opus 4.6 (Deep Thinking)",
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
    onGetAuthUrl: () -> String = { "" },
    onStartLoopbackAuth: (((String) -> Unit, (Result<Boolean>) -> Unit) -> Unit)? = null,
    onCancelLoopbackAuth: (() -> Unit)? = null,
    onImportTokenOrCode: ((String, (Result<Boolean>) -> Unit) -> Unit)? = null,
    onStartDeviceAuth: (((Result<DeviceAuthCode>) -> Unit) -> Unit)? = null,
    onPollDeviceToken: ((String, (Result<Boolean>) -> Unit) -> Unit)? = null,
    onSaveKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyDraft by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember(authState.selectedModel) { mutableStateOf(authState.selectedModel) }
    var manualTokenInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = {
            onCancelLoopbackAuth?.invoke()
            onDismiss()
        },
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
                    text = "Google DeepMind Advanced Agentic Coding с контекстным окном до 2M токенов, официальными моделями Gemini 3.8/3.7 и потоковым выводом рассуждений.",
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Авторизация Google OAuth",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Войдите через браузер для официального доступа к Gemini 3.8/3.7 и Antigravity CLI без ограничений.",
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

                            if (authState.isWaitingBrowserAuth) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Ожидание входа в браузере...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Подтвердите вход в Google и вернитесь в PocketCLI.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (onCancelLoopbackAuth != null) {
                                            TextButton(onClick = onCancelLoopbackAuth) {
                                                Text("Отмена", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    authError = null
                                    if (onStartLoopbackAuth != null) {
                                        onStartLoopbackAuth(
                                            { authUrl ->
                                                uriHandler.openUri(authUrl)
                                            },
                                            { result ->
                                                result.onFailure { e ->
                                                    authError = e.message ?: "Ошибка авторизации через браузер"
                                                }
                                            }
                                        )
                                    } else {
                                        val url = onGetAuthUrl().ifEmpty {
                                            AntigravityAuthManager.getGoogleAuthUrl()
                                        }
                                        uriHandler.openUri(url)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (authState.isWaitingBrowserAuth) "Открыть браузер снова" else "Войти через Google (Браузер)")
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "Или вставьте код из редиректа / токен / ключ:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = manualTokenInput,
                                onValueChange = { manualTokenInput = it },
                                label = { Text("URL, код или токен") },
                                placeholder = { Text("http://127.0.0.1:.../oauth2callback?code=... или токен") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                manualTokenInput = clip.trim()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Вставить из буфера")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    if (manualTokenInput.isNotBlank()) {
                                        isImporting = true
                                        authError = null
                                        if (onImportTokenOrCode != null) {
                                            onImportTokenOrCode(manualTokenInput) { res ->
                                                isImporting = false
                                                res.onFailure { e ->
                                                    authError = e.message ?: "Ошибка активации токена"
                                                }
                                            }
                                        } else {
                                            onSaveKey(manualTokenInput)
                                            isImporting = false
                                        }
                                    }
                                },
                                enabled = manualTokenInput.isNotBlank() && !isImporting,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Применить")
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
                    "gemini-3.8-flash-high" to "Gemini 3.8 Flash (High Reasoning - Флагман 2026)",
                    "gemini-3.8-flash-medium" to "Gemini 3.8 Flash (Medium Reasoning)",
                    "gemini-3.8-flash-low" to "Gemini 3.8 Flash (Низкая задержка)",
                    "gemini-3.7-flash-high" to "Gemini 3.7 Flash (High Reasoning)",
                    "gemini-3.6-flash-high" to "Gemini 3.6 Flash (High Reasoning)",
                    "gemini-3.1-pro-high" to "Gemini 3.1 Pro (Advanced Reasoning)",
                    "claude-sonnet-4-6" to "Claude Sonnet 4.6 (Thinking)",
                    "claude-opus-4-6-thinking" to "Claude Opus 4.6 (Deep Thinking)",
                    "gpt-oss-120b-medium" to "GPT-OSS 120B (Medium)"
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
