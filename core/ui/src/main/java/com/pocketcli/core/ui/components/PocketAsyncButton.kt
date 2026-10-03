package com.pocketcli.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.LocalPocketMotionScheme
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.core.ui.theme.PocketMotion
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.core.ui.theme.ToolSuccessColor

sealed interface AsyncActionState {
    data object Idle : AsyncActionState
    data object Loading : AsyncActionState
    data object Success : AsyncActionState
    data class Error(val message: String? = null) : AsyncActionState
}

/**
 * PocketAsyncButton - unified morphing action button for async tasks.
 * Minimum 48dp touch target, state-based color transition, no fake progress.
 */
@Composable
fun PocketAsyncButton(
    state: AsyncActionState,
    onClick: () -> Unit,
    label: String,
    successLabel: String,
    errorLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val motion = LocalPocketMotionScheme.current

    val containerColor by animateColorAsState(
        targetValue = when (state) {
            AsyncActionState.Idle, AsyncActionState.Loading -> MaterialTheme.colorScheme.primary
            AsyncActionState.Success -> ToolSuccessColor
            is AsyncActionState.Error -> MaterialTheme.colorScheme.error
        },
        animationSpec = PocketMotion.standard(),
        label = "AsyncButtonContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when (state) {
            AsyncActionState.Idle, AsyncActionState.Loading -> MaterialTheme.colorScheme.onPrimary
            AsyncActionState.Success -> Color.White
            is AsyncActionState.Error -> MaterialTheme.colorScheme.onError
        },
        animationSpec = PocketMotion.standard(),
        label = "AsyncButtonContentColor"
    )

    Button(
        onClick = onClick,
        enabled = enabled && state !is AsyncActionState.Loading && state !is AsyncActionState.Success,
        shape = PocketShapes.action,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.8f),
            disabledContentColor = contentColor.copy(alpha = 0.9f)
        ),
        contentPadding = PaddingValues(horizontal = PocketSpacing.lg, vertical = PocketSpacing.sm),
        modifier = modifier
            .heightIn(min = 48.dp)
            .defaultMinSize(minWidth = 120.dp)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
            },
            label = "AsyncButtonContent"
        ) { targetState ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (targetState) {
                    AsyncActionState.Idle -> {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    AsyncActionState.Loading -> {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(PocketSpacing.xs))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    AsyncActionState.Success -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(PocketSpacing.xs))
                        Text(
                            text = successLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    is AsyncActionState.Error -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(PocketSpacing.xs))
                        Text(
                            text = targetState.message ?: errorLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "AsyncButton States Light")
@Composable
fun PocketAsyncButtonPreview() {
    PocketCLITheme(darkTheme = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PocketAsyncButton(
                    state = AsyncActionState.Idle,
                    onClick = {},
                    label = "Установить окружение",
                    successLabel = "Установлено",
                    errorLabel = "Ошибка"
                )
                PocketAsyncButton(
                    state = AsyncActionState.Loading,
                    onClick = {},
                    label = "Установка...",
                    successLabel = "Установлено",
                    errorLabel = "Ошибка"
                )
                PocketAsyncButton(
                    state = AsyncActionState.Success,
                    onClick = {},
                    label = "Установить окружение",
                    successLabel = "Готово к работе",
                    errorLabel = "Ошибка"
                )
                PocketAsyncButton(
                    state = AsyncActionState.Error("Сбой сети"),
                    onClick = {},
                    label = "Повторить",
                    successLabel = "Готово",
                    errorLabel = "Ошибка сети"
                )
            }
        }
    }
}