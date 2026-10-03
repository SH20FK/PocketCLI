package com.pocketcli.feature.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.core.ui.theme.ToolSuccessColor

/**
 * DiffSummaryItem according to section 4.5:
 * - Summary row: e.g. 3 файла · +48 −12
 * - Up to three file paths
 * - Открыть diff button (min 48dp touch target)
 * - The entire raw diff is not inserted inline into the chat timeline
 */
@Composable
fun DiffSummaryItem(
    filePath: String,
    onOpenDiff: () -> Unit,
    modifier: Modifier = Modifier,
    fileCount: Int = 1,
    additions: Int = 0,
    deletions: Int = 0
) {
    Card(
        shape = PocketShapes.compact,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(PocketShapes.compact)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PocketSpacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Difference,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(PocketSpacing.xs))
                    val summaryText = buildString {
                        if (fileCount > 1) {
                            append( файла · )
                        }
                        if (additions > 0 || deletions > 0) {
                            append(+ −)
                        } else {
                            append(Изменения в коде)
                        }
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledTonalButton(
                    onClick = onOpenDiff,
                    shape = PocketShapes.action,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(Открыть diff)
                }
            }

            Spacer(modifier = Modifier.height(PocketSpacing.xxs))

            Text(
                text = filePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}