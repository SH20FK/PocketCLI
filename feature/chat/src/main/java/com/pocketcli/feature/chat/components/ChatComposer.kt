package com.pocketcli.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.feature.chat.model.ComposerMode
import com.pocketcli.feature.chat.model.ComposerState

/**
 * ChatComposer according to section 4.2:
 * - Collapsed: 56 dp, typing: 56-136 dp, attachments: +52 dp strip
 * - Outer margin 8 dp, inner padding 12 dp
 * - Top: AttachmentStrip
 * - Center: Auto-expanding text input
 * - Bottom action row:
 *   - + (48x48) opens attach menu
 *   - model chip (short model name, taps opens ModelPickerSheet)
 *   - flexible spacer
 *   - Send / Stop button morph in 48x48 slot
 */
@Composable
fun ChatComposer(
    state: ComposerState,
    onDraftChange: (String) -> Unit,
    onAddAttachment: (String) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onOpenModelPicker: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val isRunning = state.mode == ComposerMode.Running || state.mode == ComposerMode.Sending
    val canSend = (state.text.isNotBlank() || state.attachments.isNotEmpty()) && !isRunning

    Surface(
        shape = PocketShapes.action,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.xs, vertical = PocketSpacing.xxs)
            .clip(PocketShapes.action)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PocketSpacing.sm)
        ) {
            // 1. Attachment strip
            AttachmentStrip(
                attachments = state.attachments,
                onRemoveAttachment = onRemoveAttachment
            )

            // 2. Text input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp, max = 136.dp)
                    .padding(horizontal = PocketSpacing.xs, vertical = PocketSpacing.xxs)
            ) {
                if (state.text.isEmpty()) {
                    Text(
                        text = "Сообщение или / для команд...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = state.text,
                    onValueChange = onDraftChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(PocketSpacing.xs))

            // 3. Bottom action row: [+] [Model Chip] [Spacer] [Send/Stop]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Attach button (+)
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Прикрепить файл или действие",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Файл из проекта") },
                            leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                onAddAttachment("файл проекта")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Изображение") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                onAddAttachment("изображение")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Снимок экрана / Камера") },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                onAddAttachment("камера")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Рабочий контекст") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                onAddAttachment("контекст")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(PocketSpacing.xs))

                // Short Model chip
                Surface(
                    shape = PocketShapes.compact,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .height(36.dp)
                        .clip(PocketShapes.compact)
                        .clickable(onClick = onOpenModelPicker)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = PocketSpacing.sm)
                    ) {
                        Text(
                            text = state.selectedModelName ?: "Модель",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(PocketSpacing.xxs))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Выбрать модель",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Send / Stop morph in 48x48 slot
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isRunning,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
                        },
                        label = "SendStopMorph"
                    ) { running ->
                        if (running) {
                            FilledIconButton(
                                onClick = onStop,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Остановить",
                                    tint = MaterialTheme.colorScheme.onError
                                )
                            }
                        } else {
                            FilledIconButton(
                                onClick = onSend,
                                enabled = canSend,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}