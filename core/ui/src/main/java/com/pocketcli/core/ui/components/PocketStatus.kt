package com.pocketcli.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.core.ui.theme.SemanticSuccess
import com.pocketcli.core.ui.theme.SemanticWarning

enum class PocketStatusState(val label: String) {
    IDLE("Готов"),
    READY("Готов"),
    THINKING("Думает"),
    RUNNING("Выполняет"),
    EXECUTING("Выполняет"),
    WAITING_PERMISSION("Ждёт подтверждения"),
    AWAITING_PERMISSION("Ждёт подтверждения"),
    ERROR("Ошибка"),
    OFFLINE("Офлайн")
}

/**
 * PocketStatus renders either a compact status glyph (8dp dot/icon) or a full status pill
 * according to Section 10 of POCKETCLI_UI_IMPLEMENTATION_PACK.md.
 */
@Composable
fun PocketStatus(
    state: PocketStatusState,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    customLabel: String? = null
) {
    val text = customLabel ?: state.label
    val (color, icon) = when (state) {
        PocketStatusState.IDLE, PocketStatusState.READY -> Pair(SemanticSuccess, Icons.Default.CheckCircle)
        PocketStatusState.THINKING -> Pair(MaterialTheme.colorScheme.primary, Icons.Default.Psychology)
        PocketStatusState.RUNNING, PocketStatusState.EXECUTING -> Pair(MaterialTheme.colorScheme.tertiary, Icons.Default.Terminal)
        PocketStatusState.WAITING_PERMISSION, PocketStatusState.AWAITING_PERMISSION -> Pair(SemanticWarning, Icons.Default.Security)
        PocketStatusState.ERROR -> Pair(MaterialTheme.colorScheme.error, Icons.Default.ErrorOutline)
        PocketStatusState.OFFLINE -> Pair(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), Icons.Default.CloudOff)
    }

    if (compact) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(PocketSpacing.xs))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Surface(
            shape = PocketShapes.action,
            color = color.copy(alpha = 0.12f),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = PocketSpacing.sm, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
        }
    }
}
