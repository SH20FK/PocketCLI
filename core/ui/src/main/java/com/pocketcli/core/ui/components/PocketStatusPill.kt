package com.pocketcli.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketCustomShapes
import com.pocketcli.core.ui.theme.SemanticWarning
import com.pocketcli.core.ui.theme.ToolRunningColor
import com.pocketcli.core.ui.theme.ToolSuccessColor

enum class AgentActivityState {
    READY,              // Готов
    THINKING,           // Думает
    EXECUTING,          // Выполняет
    AWAITING_PERMISSION,// Ждёт разрешения
    OFFLINE             // Офлайн
}

@Composable
fun PocketStatusPill(
    state: AgentActivityState,
    modifier: Modifier = Modifier,
    customText: String? = null,
    onClick: (() -> Unit)? = null
) {
    val (label, icon, containerColor, contentColor) = when (state) {
        AgentActivityState.READY -> Quad(
            customText ?: "Готов",
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        AgentActivityState.THINKING -> Quad(
            customText ?: "Думает...",
            Icons.Default.Psychology,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        AgentActivityState.EXECUTING -> Quad(
            customText ?: "Выполняет",
            Icons.Default.Terminal,
            ToolRunningColor.copy(alpha = 0.2f),
            ToolRunningColor
        )
        AgentActivityState.AWAITING_PERMISSION -> Quad(
            customText ?: "Ждёт подтверждения",
            Icons.Default.WarningAmber,
            SemanticWarning.copy(alpha = 0.2f),
            SemanticWarning
        )
        AgentActivityState.OFFLINE -> Quad(
            customText ?: "Офлайн",
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val clickModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Surface(
        shape = PocketCustomShapes.StatusPill,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .clip(PocketCustomShapes.StatusPill)
            .then(clickModifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (state == AgentActivityState.THINKING) {
                // Subtle pulse for thinking
                val infiniteTransition = rememberInfiniteTransition(label = "ThinkingPulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = alpha))
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
