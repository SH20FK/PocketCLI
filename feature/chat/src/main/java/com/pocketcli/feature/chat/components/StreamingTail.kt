package com.pocketcli.feature.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.LocalPocketMotionScheme
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.feature.chat.model.AgentPhase
import com.pocketcli.feature.chat.model.StreamingTailUi

/**
 * StreamingTail according to section 4.2:
 * - Living indicator after the last line of the active response
 * - Displays human-readable phase description
 * - TalkBack gets semantic status updates
 */
@Composable
fun StreamingTail(
    tail: StreamingTailUi,
    modifier: Modifier = Modifier
) {
    val phaseDescription = when (tail.phase) {
        AgentPhase.THINKING -> "Думает..."
        AgentPhase.STREAMING_TEXT -> "Пишет..."
        AgentPhase.EXECUTING_TOOL -> tail.currentTool?.let { "Выполняет: ${it.name}" } ?: "Выполняет действие..."
        AgentPhase.AWAITING_PERMISSION -> "Ожидает подтверждения..."
        AgentPhase.COMPLETED -> "Готово"
        AgentPhase.ERROR -> "Ошибка"
        AgentPhase.IDLE -> ""
    }

    if (tail.phase == AgentPhase.IDLE || tail.phase == AgentPhase.COMPLETED) return

    val infiniteTransition = rememberInfiniteTransition(label = "StreamingTailTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LivingAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PocketSpacing.xs)
            .semantics {
                contentDescription = "Агент $phaseDescription"
            }
    ) {
 Box(
 modifier = Modifier
 .size(8.dp)
 .clip(CircleShape)
 .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
 )
 Spacer(modifier = Modifier.width(PocketSpacing.xs))
 Text(
 text = phaseDescription,
 style = MaterialTheme.typography.labelMedium,
 color = MaterialTheme.colorScheme.onSurfaceVariant
 )
 }
}