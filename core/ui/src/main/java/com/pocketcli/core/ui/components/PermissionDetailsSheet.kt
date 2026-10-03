package com.pocketcli.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.PermissionOption
import com.pocketcli.core.ui.theme.MonospaceCodeStyle
import com.pocketcli.core.ui.theme.SemanticWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDetailsSheet(
    requestId: String,
    title: String,
    commandOrPayload: String?,
    workingDirectory: String?,
    onReply: (requestId: String, option: PermissionOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = SemanticWarning,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Запрос разрешения",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!workingDirectory.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Директория: $workingDirectory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!commandOrPayload.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = commandOrPayload,
                        style = MonospaceCodeStyle,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons: Reject, Once, Always
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        onReply(requestId, PermissionOption.REJECT)
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отклонить")
                }

                FilledTonalButton(
                    onClick = {
                        onReply(requestId, PermissionOption.ONCE)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Один раз")
                }

                Button(
                    onClick = {
                        onReply(requestId, PermissionOption.ALWAYS)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Для сессии")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
