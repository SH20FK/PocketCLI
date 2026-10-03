package com.pocketcli.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketSpacing

/**
 * AttachmentStrip according to section 4.2:
 * - 52dp height strip above composer input
 * - Shows chips with close buttons
 * - Animated fade and slide transitions
 */
@Composable
fun AttachmentStrip(
    attachments: List<String>,
    onRemoveAttachment: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = attachments.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PocketSpacing.sm, vertical = PocketSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            attachments.forEachIndexed { index, name ->
                InputChip(
                    selected = true,
                    onClick = { onRemoveAttachment(index) },
                    label = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRemoveAttachment(index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить вложение",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}