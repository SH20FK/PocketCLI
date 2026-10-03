package com.pocketcli.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketcli.core.security.SharedPreferencesProviderKeyStore

@Composable
fun ProviderKeysDialog(
    currentKeys: Map<String, String>,
    onSaveKey: (envVar: String, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    val draftKeys = remember(currentKeys) {
        mutableStateMapOf<String, String>().apply {
            putAll(currentKeys)
        }
    }

    val visibilityMap = remember {
        mutableStateMapOf<String, Boolean>()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("API-ключи провайдеров")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ключи шифруются в Android Keystore и передаются локальному агенту через переменные окружения процесса.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SharedPreferencesProviderKeyStore.KNOWN_PROVIDERS.forEach { (envVar, label) ->
                    val isVisible = visibilityMap[envVar] ?: false
                    val value = draftKeys[envVar].orEmpty()

                    OutlinedTextField(
                        value = value,
                        onValueChange = { draftKeys[envVar] = it },
                        label = { Text(label) },
                        placeholder = { Text(envVar) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { visibilityMap[envVar] = !isVisible }) {
                                Icon(
                                    imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isVisible) "Скрыть" else "Показать"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    draftKeys.forEach { (envVar, value) ->
                        onSaveKey(envVar, value)
                    }
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
