package com.pocketcli.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.components.PocketDialog

@Composable
fun CreateProjectDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (displayName: String, initReadme: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var initGit by remember { mutableStateOf(true) }
    var initReadme by remember { mutableStateOf(true) }
    var initialBranch by remember { mutableStateOf("main") }

    PocketDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = "Создать пустой проект",
        icon = {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, initReadme) },
                enabled = name.isNotBlank() && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Создать проект")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Отмена")
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название проекта") },
                placeholder = { Text("my-new-app") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (name.isNotBlank()) {
                val slug = name.lowercase().replace(" ", "-")
                Text(
                    text = "Путь: ~/workspaces/$slug",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Инициализировать Git", style = MaterialTheme.typography.bodyMedium)
                    Text("Ветка: $initialBranch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = initGit, onCheckedChange = { initGit = it })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать README.md", style = MaterialTheme.typography.bodyMedium)
                Checkbox(checked = initReadme, onCheckedChange = { initReadme = it })
            }
        }
    }
}
