package com.pocketcli.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectFabMenu(
    onCloneClick: () -> Unit,
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ExtendedFloatingActionButton(
            text = { Text("Добавить проект") },
            icon = { Icon(Icons.Default.Add, contentDescription = "Добавить проект") },
            onClick = { showSheet = true }
        )

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Добавить проект",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    ListItem(
                        headlineContent = { Text("Клонировать Git-репозиторий") },
                        supportingContent = { Text("Из GitHub, GitLab или другого хостинга") },
                        leadingContent = {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSheet = false
                                onCloneClick()
                            }
                    )

                    ListItem(
                        headlineContent = { Text("Создать пустой проект") },
                        supportingContent = { Text("Новое изолированное рабочее пространство") },
                        leadingContent = {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSheet = false
                                onCreateClick()
                            }
                    )

                    ListItem(
                        headlineContent = { Text("Импортировать копию папки") },
                        supportingContent = { Text("Копировать существующую папку в PocketCLI") },
                        leadingContent = {
                            Icon(Icons.Default.DriveFolderUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSheet = false
                                onImportClick()
                            }
                    )
                }
            }
        }
    }
}

