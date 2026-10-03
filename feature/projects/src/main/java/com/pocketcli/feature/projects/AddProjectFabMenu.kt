package com.pocketcli.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddProjectFabMenu(
    onCloneClick: () -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ExtendedFloatingActionButton(
            text = { Text("Добавить проект") },
            icon = { Icon(Icons.Default.Add, contentDescription = "Добавить проект") },
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Клонировать Git-репозиторий", style = MaterialTheme.typography.bodyMedium)
                        Text("Из GitHub, GitLab или другого хостинга", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                    expanded = false
                    onCloneClick()
                }
            )

            DropdownMenuItem(
                text = {
                    Column {
                        Text("Создать пустой проект", style = MaterialTheme.typography.bodyMedium)
                        Text("Новое изолированное рабочее пространство", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                    expanded = false
                    onCreateClick()
                }
            )

            DropdownMenuItem(
                text = {
                    Column {
                        Text("Импортировать копию папки", style = MaterialTheme.typography.bodyMedium)
                        Text("Копировать существующую папку в PocketCLI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = {
                    Icon(Icons.Default.DriveFolderUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                    expanded = false
                    onImportClick()
                }
            )
        }
    }
}
