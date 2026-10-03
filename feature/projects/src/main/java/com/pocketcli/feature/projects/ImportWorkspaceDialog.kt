package com.pocketcli.feature.projects

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.components.PocketDialog

@Composable
fun ImportWorkspaceDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onImport: (displayName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var folderName by remember { mutableStateOf("") }

    PocketDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = "Импортировать копию папки",
        icon = {
            Icon(
                imageVector = Icons.Default.DriveFolderUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onImport(folderName) },
                enabled = folderName.isNotBlank() && !isImporting
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Импортировать копию")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting
            ) {
                Text("Отмена")
            }
        },
        modifier = modifier
    ) {
        Text(
            text = "Проект будет скопирован в изолированное рабочее пространство PocketCLI. Это необходимо для корректной работы PRoot рантайма и инструментов Git.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = folderName,
            onValueChange = { folderName = it },
            label = { Text("Имя проекта") },
            placeholder = { Text("my-local-project") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
