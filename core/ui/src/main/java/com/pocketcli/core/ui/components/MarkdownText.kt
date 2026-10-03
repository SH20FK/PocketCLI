package com.pocketcli.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay

object MarkdownSanitizer {
    /**
     * Auto-closes unclosed code blocks (`) so streaming markdown does not break
     * layout or turn the entire remainder of the message into an unclosed code block.
     */
    fun normalizeForStreaming(raw: String): String {
        var inCodeBlock = false
        val lines = raw.lines()
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
            }
        }
        return if (inCodeBlock) {
            "$raw\n```"
        } else {
            raw
        }
    }
}

/**
 * StreamingMarkdownText - throttles heavy Markdown AST parsing during fast streaming.
 * Features:
 * - If isStreaming is true: parses Markdown at most 5 times/sec (200ms throttle).
 * - When streaming finishes (isStreaming becomes false), performs a single final parse.
 * - Always keeps code blocks balanced.
 */
@Composable
fun StreamingMarkdownText(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var throttledContent by remember { mutableStateOf(markdown) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }

    if (isStreaming) {
        LaunchedEffect(markdown) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastUpdateTime
            if (elapsed >= 200L) {
                throttledContent = markdown
                lastUpdateTime = now
            } else {
                delay(200L - elapsed)
                throttledContent = markdown
                lastUpdateTime = System.currentTimeMillis()
            }
        }
    } else {
        throttledContent = markdown
    }

    val normalized = remember(throttledContent) {
        MarkdownSanitizer.normalizeForStreaming(throttledContent)
    }

    try {
        Markdown(
            content = normalized,
            modifier = modifier.fillMaxWidth()
        )
    } catch (_: Throwable) {
        Text(
            text = normalized,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    StreamingMarkdownText(
        markdown = markdown,
        isStreaming = isStreaming,
        modifier = modifier
    )
}