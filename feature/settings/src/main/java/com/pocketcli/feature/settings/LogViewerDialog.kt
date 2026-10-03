package com.pocketcli.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LogViewerDialog(
    logs: List<String>,
    filterQuery: String,
    onFilterChange: (String) -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, filterQuery) {
        if (filterQuery.isBlank()) {
            logs
        } else {
            logs.filter { it.contains(filterQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Логи OpenCode")
                Row {
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PocketCLI Logs", text))
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = onFilterChange,
                    placeholder = { Text("Поиск в логах...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Text(
                            text = if (logs.isEmpty()) "Логи пусты" else "Ничего не найдено",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredLogs) { line ->
                                val textColor = when {
                                    line.contains("[STDERR]") || line.contains("Ошибка") || line.contains("Failed") -> Color(0xFFFF6B6B)
                                    line.contains("[STDOUT]") -> Color(0xFFD4D4D4)
                                    line.contains("[Supervisor]") -> Color(0xFF4EC9B0)
                                    else -> Color.White
                                }
                                Text(
                                    text = line,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
