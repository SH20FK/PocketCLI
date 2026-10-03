package com.pocketcli.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateProjectDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, initReadme: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var initReadme by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("New Local Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("my-new-app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { initReadme = !initReadme }
                ) {
                    Checkbox(
                        checked = initReadme,
                        onCheckedChange = { initReadme = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Initialize with README.md",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), initReadme) },
                enabled = !isCreating && name.isNotBlank()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating...")
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
}
