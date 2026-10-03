package com.pocketcli.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcli.core.ui.theme.DiffAddedBackgroundDark
import com.pocketcli.core.ui.theme.DiffAddedBackgroundLight
import com.pocketcli.core.ui.theme.DiffRemovedBackgroundDark
import com.pocketcli.core.ui.theme.DiffRemovedBackgroundLight
import com.pocketcli.core.ui.theme.MonospaceCodeStyle

data class DiffHunkLine(
    val type: DiffLineType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val content: String
)

enum class DiffLineType {
    ADDED,
    REMOVED,
    CONTEXT,
    HEADER
}

@Composable
fun DiffSummaryCard(
    filePath: String,
    addedLines: Int,
    removedLines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Difference,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+$addedLines",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "-$removedLines",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onClick) {
                    Text("Diff")
                }
            }
        }
    }
}

@Composable
fun FileDiffViewer(
    filePath: String,
    rawUnifiedDiff: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = remember(rawUnifiedDiff) { parseUnifiedDiff(rawUnifiedDiff) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onClose) {
                    Text("Закрыть")
                }
            }

            // Diff lines
            val horizontalScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    for (line in lines) {
                        val (bgColor, textColor) = when (line.type) {
                            DiffLineType.ADDED -> Pair(DiffAddedBackgroundDark.copy(alpha = 0.4f), Color(0xFF81C784))
                            DiffLineType.REMOVED -> Pair(DiffRemovedBackgroundDark.copy(alpha = 0.4f), Color(0xFFE57373))
                            DiffLineType.HEADER -> Pair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary)
                            DiffLineType.CONTEXT -> Pair(Color.Transparent, MaterialTheme.colorScheme.onSurface)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = (line.oldLineNumber?.toString() ?: "").padStart(4),
                                style = MonospaceCodeStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.outline),
                                modifier = Modifier.width(32.dp)
                            )
                            Text(
                                text = (line.newLineNumber?.toString() ?: "").padStart(4),
                                style = MonospaceCodeStyle.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.outline),
                                modifier = Modifier.width(32.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = line.content,
                                style = MonospaceCodeStyle.copy(color = textColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseUnifiedDiff(raw: String): List<DiffHunkLine> {
    val result = mutableListOf<DiffHunkLine>()
    var oldLine = 1
    var newLine = 1

    for (rawLine in raw.lines()) {
        when {
            rawLine.startsWith("@@") -> {
                result.add(DiffHunkLine(DiffLineType.HEADER, null, null, rawLine))
            }
            rawLine.startsWith("+") -> {
                result.add(DiffHunkLine(DiffLineType.ADDED, null, newLine++, rawLine))
            }
            rawLine.startsWith("-") -> {
                result.add(DiffHunkLine(DiffLineType.REMOVED, oldLine++, null, rawLine))
            }
            else -> {
                result.add(DiffHunkLine(DiffLineType.CONTEXT, oldLine++, newLine++, rawLine))
            }
        }
    }
    return result
}
