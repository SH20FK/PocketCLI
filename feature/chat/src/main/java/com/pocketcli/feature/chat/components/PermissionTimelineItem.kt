package com.pocketcli.feature.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.PermissionOption
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing

/**
 * PermissionTimelineItem according to section 4.4:
 * - tertiaryContainer, radius 14 dp (PocketShapes.compact)
 * - Title: human language action
 * - Scope: single line
 * - Отклонить tonal, Разрешить filled
 * - Minimum 48 dp interactive touch targets
 */
@Composable
fun PermissionTimelineItem(
    requestId: String,
    title: String,
    onReply: (requestId: String, option: PermissionOption) -> Unit,
    modifier: Modifier = Modifier,
    scopeSummary: String? = null,
    onShowDetails: (() -> Unit)? = null
) {
    Card(
        shape = PocketShapes.compact,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(PocketSpacing.xs))
                Text(
                    text = "Запрос разрешения",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(PocketSpacing.xs))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!scopeSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(PocketSpacing.xxs))
                Text(
                    text = scopeSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(PocketSpacing.sm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onReply(requestId, PermissionOption.REJECT) },
                    shape = PocketShapes.action,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Отклонить")
                }

                Button(
                    onClick = { onReply(requestId, PermissionOption.ONCE) },
                    shape = PocketShapes.action,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Разрешить")
                }
            }
        }
    }
}