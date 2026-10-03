package com.pocketcli.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.ActiveSessionInfo
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing

/**
 * ActiveSessionBar according to section 3 & 6:
 * - PocketShapes.container (20.dp)
 * - 0dp shadow, tone-based surfaceContainerHigh
 * - Compact status glyph (PocketStatus)
 * - Minimum 48dp touch targets on actions
 */
@Composable
fun ActiveSessionBar(
    sessionInfo: ActiveSessionInfo?,
    onOpenSession: (sessionId: String, profileId: String) -> Unit,
    onStopSession: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = sessionInfo != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (sessionInfo != null) {
            Surface(
                shape = PocketShapes.container,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PocketSpacing.xs, vertical = PocketSpacing.xxs)
                    .clip(PocketShapes.container)
                    .clickable { onOpenSession(sessionInfo.sessionId, sessionInfo.profileId) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = PocketSpacing.sm, vertical = PocketSpacing.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        PocketStatus(state = PocketStatusState.RUNNING, compact = true)
                        Spacer(modifier = Modifier.width(PocketSpacing.xs))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sessionInfo.projectName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sessionInfo.currentAction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xxs)
                    ) {
                        FilledTonalIconButton(
                            onClick = { onStopSession(sessionInfo.sessionId) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = Остановить сессию,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { onOpenSession(sessionInfo.sessionId, sessionInfo.profileId) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = Открыть чат,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}