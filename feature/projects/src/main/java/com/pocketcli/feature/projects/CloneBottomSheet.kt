package com.pocketcli.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneBottomSheet(
    isCloning: Boolean,
    onDismiss: () -> Unit,
    onClone: (url: String, name: String, branch: String, token: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var isShallow by remember { mutableStateOf(true) }
    var isPrivate by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var autoNameModified by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!isCloning) onDismiss() },
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
            Text(
                text = "Clone Git Repository",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = url,
                onValueChange = { newUrl ->
                    url = newUrl
                    if (!autoNameModified) {
                        val inferred = newUrl.trim()
                            .substringAfterLast("/")
                            .removeSuffix(".git")
                        if (inferred.isNotBlank()) {
                            name = inferred
                        }
                    }
                },
                label = { Text("Repository URL") },
                placeholder = { Text("https://github.com/user/repo.git") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    autoNameModified = true
                },
                label = { Text("Project Name") },
                placeholder = { Text("my-project") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it },
                label = { Text("Branch") },
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
                        text = "Saves disk space and mobile bandwidth",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isShallow,
                    onCheckedChange = { isShallow = it }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Private Repository",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Requires Personal Access Token (PAT)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPrivate,
                    onCheckedChange = { isPrivate = it }
                )
            }

            if (isPrivate) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal Access Token") },
                    placeholder = { Text("ghp_...") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "Hide token" else "Show token"
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    supportingText = {
                        Text("Encrypted with Android Keystore AES-256-GCM. Never saved in .git/config.")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isCloning
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val auth = if (isPrivate && token.isNotBlank()) token.trim() else null
                        onClone(url.trim(), name.trim(), branch.trim(), auth)
                    },
                    enabled = !isCloning && url.isNotBlank()
                ) {
                    if (isCloning) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cloning...")
                    } else {
                        Text("Clone")
                    }
                }
            }
        }
    }
}
