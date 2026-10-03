package com.pocketcli.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class StateGlyph {
    IDLE,
    SEND,
    STOP,
    PLAY,
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR,
    EXPAND,
    COLLAPSE,
    SYNC
}

/**
 * PocketAnimatedStateIcon - morphs or fades between state glyphs according to motion system rules.
 * If reduceMotion is true, uses an instant or 100ms fade instead of longer transitions.
 */
@Composable
fun PocketAnimatedStateIcon(
    targetGlyph: StateGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    tint: Color = Color.Unspecified
) {
    val duration = if (reduceMotion) 100 else 180

    AnimatedContent(
        targetState = targetGlyph,
        transitionSpec = {
            fadeIn(animationSpec = tween(duration)) togetherWith fadeOut(animationSpec = tween(duration))
        },
        label = PocketAnimatedStateIcon
    ) { glyph ->
        val icon: ImageVector = when (glyph) {
            StateGlyph.IDLE -> Icons.Default.Circle
            StateGlyph.SEND -> Icons.Default.Send
            StateGlyph.STOP -> Icons.Default.Stop
            StateGlyph.PLAY -> Icons.Default.PlayArrow
            StateGlyph.PENDING -> Icons.Default.HourglassEmpty
            StateGlyph.RUNNING -> Icons.Default.Sync
            StateGlyph.SUCCESS -> Icons.Default.Check
            StateGlyph.ERROR -> Icons.Default.ErrorOutline
            StateGlyph.EXPAND -> Icons.Default.ExpandMore
            StateGlyph.COLLAPSE -> Icons.Default.ExpandLess
            StateGlyph.SYNC -> Icons.Default.Sync
        }

        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}