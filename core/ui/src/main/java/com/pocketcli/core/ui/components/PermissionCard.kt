package com.pocketcli.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.PermissionOption
import com.pocketcli.core.ui.theme.SemanticWarning

@Composable
fun PermissionCard(
    requestId: String,
    title: String,
    onReply: (requestId: String, option: PermissionOption) -> Unit,
    modifier: Modifier = Modifier,
    onShowDetails: (() -> Unit)? = null
) {
    val clickModifier = if (onShowDetails != null) Modifier.clickable { onShowDetails() } else Modifier

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(clickModifier)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Требуется разрешение",
                        tint = SemanticWarning,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Требуется разрешение",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                if (onShowDetails != null) {
                    IconButton(onClick = onShowDetails, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Подробнее",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onReply(requestId, PermissionOption.REJECT) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отклонить")
                }

                FilledTonalButton(
                    onClick = { onReply(requestId, PermissionOption.ONCE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Разрешить")
                }
            }
        }
    }
}
