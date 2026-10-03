package com.pocketcli.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.ToolCall
import com.pocketcli.core.model.ToolStatus
import com.pocketcli.core.ui.theme.ToolErrorColor
import com.pocketcli.core.ui.theme.ToolRunningColor
import com.pocketcli.core.ui.theme.ToolSuccessColor

@Composable
fun ToolCallCard(
    toolCall: ToolCall,
    onOpenDetails: (ToolCall) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (toolCall.status) {
        ToolStatus.RUNNING -> ToolRunningColor
        ToolStatus.COMPLETED -> ToolSuccessColor
        ToolStatus.ERROR -> ToolErrorColor
        ToolStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when (toolCall.name.lowercase()) {
        "bash", "terminal", "command" -> Icons.Default.Terminal
        else -> Icons.Default.Code
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenDetails(toolCall) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = toolCall.name,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = toolCall.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (toolCall.status) {
                    ToolStatus.RUNNING -> {
                        CircularProgressIndicator(
                            color = ToolRunningColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    ToolStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = ToolSuccessColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    ToolStatus.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = ToolErrorColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    ToolStatus.PENDING -> Unit
                }
            }

            // Smart preview
            val preview = rememberSmartPreview(toolCall)
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun rememberSmartPreview(toolCall: ToolCall): String {
    val output = toolCall.output.orEmpty()
    return if (toolCall.name.equals("bash", ignoreCase = true)) {
        // Show tail of bash output
        val lines = output.lines().filter { it.isNotBlank() }
        if (lines.size > 3) lines.takeLast(3).joinToString("\n") else output
    } else {
        output.take(200)
    }
}
