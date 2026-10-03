package com.pocketcli.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketMotion

enum class IconState {
    SEND,
    STOP,
    PLAY,
    EXPAND,
    COLLAPSE,
    SYNC,
    CHECK,
    VISIBILITY_ON,
    VISIBILITY_OFF,
    MIC
}

@Composable
fun PocketAnimatedIcon(
    state: IconState,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(animationSpec = tween(PocketMotion.DURATION_QUICK)) togetherWith
            fadeOut(animationSpec = tween(PocketMotion.DURATION_QUICK))
        },
        label = "PocketAnimatedIcon"
    ) { targetState ->
        val icon = when (targetState) {
            IconState.SEND -> Icons.Default.Send
            IconState.STOP -> Icons.Default.Stop
            IconState.PLAY -> Icons.Default.PlayArrow
            IconState.EXPAND -> Icons.Default.ExpandMore
            IconState.COLLAPSE -> Icons.Default.ExpandLess
            IconState.SYNC -> Icons.Default.Sync
            IconState.CHECK -> Icons.Default.Check
            IconState.VISIBILITY_ON -> Icons.Default.Visibility
            IconState.VISIBILITY_OFF -> Icons.Default.VisibilityOff
            IconState.MIC -> Icons.Default.Mic
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

@Preview(name = "Pocket Animated Icon Preview")
@Composable
fun PocketAnimatedIconPreview() {
    Surface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            PocketAnimatedIcon(state = IconState.SEND, contentDescription = "Send")
            PocketAnimatedIcon(state = IconState.STOP, contentDescription = "Stop")
            PocketAnimatedIcon(state = IconState.PLAY, contentDescription = "Play")
            PocketAnimatedIcon(state = IconState.MIC, contentDescription = "Mic")
        }
    }
}
