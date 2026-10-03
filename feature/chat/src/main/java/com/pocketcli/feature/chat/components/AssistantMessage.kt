package com.pocketcli.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.Message
import com.pocketcli.core.model.MessageRole
import com.pocketcli.core.ui.components.MarkdownText
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.core.ui.theme.PocketSpacing

/**
 * AssistantMessage according to section 4.2:
 * - Full width (no bubble)
 * - Primary body: bodyLarge
 * - Reasoning: single row "Рассуждения агента" with expand/collapse chevron
 * - Actions row (copy) shown only on hover/tap or after stream finishes
 */
@Composable
fun AssistantMessage(
    message: Message,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val clipboardManager = LocalClipboardManager.current
    var isReasoningExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PocketSpacing.xs)
    ) {
        // Reasoning section (if available)
        if (!message.reasoning.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isReasoningExpanded = !isReasoningExpanded }
                    .padding(vertical = PocketSpacing.xxs)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(PocketSpacing.xs))
                Text(
                    text = "Рассуждения агента",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isReasoningExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isReasoningExpanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isReasoningExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PocketSpacing.xxs, bottom = PocketSpacing.sm)
                ) {
                    Text(
                        text = message.reasoning.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(PocketSpacing.sm)
                    )
                }
            }
        }

        // Primary text body
        if (message.text.isNotEmpty()) {
            MarkdownText(
                markdown = message.text,
                isStreaming = isStreaming,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Actions row (copy message)
        if (!isStreaming && message.text.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PocketSpacing.xs),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Скопировать ответ",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Preview(name = "AssistantMessage Light")
@Composable
fun AssistantMessagePreview() {
    PocketCLITheme(darkTheme = false) {
        Surface {
            AssistantMessage(
                message = Message(
                    id = "msg1",
                    sessionId = "sess1",
                    role = MessageRole.ASSISTANT,
                    text = "Тесты успешно созданы:\n\n```kotlin\n@Test\nfun testRun() {\n    assertTrue(true)\n}\n```\nВсе проверки пройдены.",
                    reasoning = "Сначала проверяем архитектуру proot, затем формируем окружение LD_LIBRARY_PATH."
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
