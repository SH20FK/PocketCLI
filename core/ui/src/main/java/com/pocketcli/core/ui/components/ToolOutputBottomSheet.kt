package com.pocketcli.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.ToolCall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolOutputBottomSheet(
    toolCall: ToolCall?,
    onDismiss: () -> Unit
) {
    if (toolCall == null) return

    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    val fullOutput = toolCall.output.orEmpty()
    val allLines = remember(fullOutput) { fullOutput.lines() }

    val filteredLines = remember(allLines, searchQuery) {
        if (searchQuery.isBlank()) {
            allLines
        } else {
            allLines.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tool Output: ${toolCall.name}",
                    style = MaterialTheme.typography.titleMedium
                )

                Row {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(fullOutput))
                    }) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Full Output")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search output lines...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Virtualized output view (LazyColumn prevents OOM/ANR)
            SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredLines) { index, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
