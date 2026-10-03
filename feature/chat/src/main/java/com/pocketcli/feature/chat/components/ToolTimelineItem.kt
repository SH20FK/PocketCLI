package com.pocketcli.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.ToolCall
import com.pocketcli.core.model.ToolStatus
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.core.ui.theme.ToolRunningColor
import com.pocketcli.core.ui.theme.ToolSuccessColor

/**
 * ToolTimelineItem according to section 4.3:
 * - Collapsed row 48–56 dp
 * - Leading 24 dp semantic icon (Terminal, Read file, Edit)
 * - Single-line human summary
 * - Trailing duration and status icon
 * - Tinted background ONLY when running or error
 * - Stdout is never shown inline; clicking opens details
 */
@Composable
fun ToolTimelineItem(
    toolCall: ToolCall,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    durationText: String? = null
) {
    val isRunning = toolCall.status == ToolStatus.RUNNING
    val isError = toolCall.status == ToolStatus.ERROR

    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isRunning -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }

    val icon: ImageVector = when {
        toolCall.name.contains("terminal", ignoreCase = true) || toolCall.name.contains("bash", ignoreCase = true) || toolCall.name.contains("exec", ignoreCase = true) -> Icons.Default.Terminal
        toolCall.name.contains("read", ignoreCase = true) || toolCall.name.contains("view", ignoreCase = true) -> Icons.Default.Visibility
        toolCall.name.contains("edit", ignoreCase = true) || toolCall.name.contains("write", ignoreCase = true) -> Icons.Default.EditNote
        toolCall.name.contains("search", ignoreCase = true) || toolCall.name.contains("grep", ignoreCase = true) || toolCall.name.contains("find", ignoreCase = true) -> Icons.Default.Search
        else -> Icons.Default.Build
    }

    val friendlyName = when {
        toolCall.name.contains("terminal", ignoreCase = true) || toolCall.name.contains("bash", ignoreCase = true) -> "Команда терминала"
        toolCall.name.contains("read", ignoreCase = true) || toolCall.name.contains("view", ignoreCase = true) -> "Чтение файла"
        toolCall.name.contains("edit", ignoreCase = true) || toolCall.name.contains("write", ignoreCase = true) -> "Изменение файла"
        toolCall.name.contains("grep", ignoreCase = true) || toolCall.name.contains("search", ignoreCase = true) -> "Поиск по коду"
        else -> toolCall.name
    }

    Surface(
        shape = PocketShapes.compact,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 56.dp)
            .clip(PocketShapes.compact)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PocketSpacing.sm, vertical = PocketSpacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isRunning) ToolRunningColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(PocketSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friendlyName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val summary = toolCall.inputJson?.take(60)?.replace("\n", " ") ?: ""
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(PocketSpacing.xs))

            if (!durationText.isNullOrBlank()) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(PocketSpacing.xs))
            }

            when (toolCall.status) {
                ToolStatus.RUNNING -> {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = ToolRunningColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                ToolStatus.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Успешно",
                        tint = ToolSuccessColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                ToolStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Ошибка",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                ToolStatus.PENDING -> {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "Ожидание",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}