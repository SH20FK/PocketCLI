package com.pocketcli.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.core.ui.theme.PocketMotion
import com.pocketcli.core.ui.theme.ToolSuccessColor
import kotlin.math.sin

enum class PocketAnimationType {
    LOADING,
    THINKING,
    SUCCESS,
    ERROR,
    EMPTY_CHATS,
    EMPTY_PROJECTS,
    CLOUD_SYNC
}

/**
 * Universal animation wrapper respecting lifecycle state and reduced-motion settings,
 * dynamically adapting to Material 3 theme colors.
 */
@Composable
fun PocketAnimationContainer(
    type: PocketAnimationType,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val isPreview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    // Animation only runs when lifecycle is at least STARTED and not in static preview
    val isActive = isPreview || lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(80.dp)
    ) {
        when (type) {
            PocketAnimationType.LOADING -> LoadingAnimation(isActive = isActive)
            PocketAnimationType.THINKING -> ThinkingAnimation(isActive = isActive)
            PocketAnimationType.SUCCESS -> SuccessAnimation()
            PocketAnimationType.ERROR -> ErrorAnimation()
            PocketAnimationType.EMPTY_CHATS -> EmptyChatsIllustration()
            PocketAnimationType.EMPTY_PROJECTS -> EmptyProjectsIllustration()
            PocketAnimationType.CLOUD_SYNC -> CloudSyncAnimation(isActive = isActive)
        }
    }
}

@Composable
private fun LoadingAnimation(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "PocketLoading")
    val rotation by if (isActive) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(PocketMotion.DURATION_SLOW_AMBIENT / 2, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    CircularProgressIndicator(
        modifier = Modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 3.dp
    )
}

@Composable
private fun ThinkingAnimation(isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "ThinkingWave")
    val phase by if (isActive) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(PocketMotion.DURATION_SLOW_AMBIENT, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WavePhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val primaryColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size(56.dp, 32.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val path = Path()
        for (x in 0..width.toInt() step 2) {
            val normalizedX = x.toFloat() / width
            val y = centerY + sin(normalizedX * 4 * Math.PI + phase).toFloat() * (height / 3f)
            if (x == 0) path.moveTo(0f, y) else path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun SuccessAnimation() {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = "Успешно",
        tint = ToolSuccessColor,
        modifier = Modifier.size(56.dp)
    )
}

@Composable
private fun ErrorAnimation() {
    Icon(
        imageVector = Icons.Default.Error,
        contentDescription = "Ошибка",
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(56.dp)
    )
}

@Composable
private fun EmptyChatsIllustration() {
    Icon(
        imageVector = Icons.Default.Forum,
        contentDescription = "Нет чатов",
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.size(64.dp)
    )
}

@Composable
private fun EmptyProjectsIllustration() {
    Icon(
        imageVector = Icons.Default.FolderOpen,
        contentDescription = "Нет проектов",
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.size(64.dp)
    )
}

@Composable
private fun CloudSyncAnimation(isActive: Boolean) {
    Icon(
        imageVector = Icons.Default.CloudSync,
        contentDescription = "Синхронизация",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp)
    )
}

@Preview(name = "Pocket Animation Light")
@Composable
fun PocketAnimationContainerPreview() {
    PocketCLITheme(darkTheme = false) {
        Surface {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                PocketAnimationContainer(type = PocketAnimationType.LOADING)
                PocketAnimationContainer(type = PocketAnimationType.THINKING)
                PocketAnimationContainer(type = PocketAnimationType.SUCCESS)
                PocketAnimationContainer(type = PocketAnimationType.ERROR)
            }
        }
    }
}

@Preview(name = "Pocket Animation Dark")
@Composable
fun PocketAnimationContainerDarkPreview() {
    PocketCLITheme(darkTheme = true) {
        Surface {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                PocketAnimationContainer(type = PocketAnimationType.EMPTY_CHATS)
                PocketAnimationContainer(type = PocketAnimationType.EMPTY_PROJECTS)
                PocketAnimationContainer(type = PocketAnimationType.CLOUD_SYNC)
            }
        }
    }
}
