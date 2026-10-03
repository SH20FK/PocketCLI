package com.pocketcli.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown

object MarkdownSanitizer {
    /**
     * Auto-closes unclosed code blocks (```) so streaming markdown does not break
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

@Composable
fun StreamingMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val normalized = remember(markdown) {
        MarkdownSanitizer.normalizeForStreaming(markdown)
    }

    Markdown(
        content = normalized,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    StreamingMarkdownText(
        markdown = markdown,
        modifier = modifier
    )
}

