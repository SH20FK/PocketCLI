package com.pocketcli.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcli.core.ui.theme.LocalPocketCodeScheme
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing

/**
 * PocketTechnicalSurface - strict technical surface for code, diff, terminal, and logs.
 * Features:
 * - 10dp technical shape (PocketShapes.technical)
 * - Header row for metadata, language tag, and actions
 * - Content area with code theme colors and monospace support
 */
@Composable
fun PocketTechnicalSurface(
    modifier: Modifier = Modifier,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val codeScheme = LocalPocketCodeScheme.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(PocketShapes.technical),
        shape = PocketShapes.technical,
        color = codeScheme.background,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PocketSpacing.sm, vertical = PocketSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    content = header
                )
            }

            HorizontalDivider(color = codeScheme.gutter.copy(alpha = 0.5f))

            // Body content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PocketSpacing.sm),
                content = content
            )
        }
    }
}

@Preview(name = PocketTechnicalSurface Light)
@Composable
fun PocketTechnicalSurfaceLightPreview() {
    PocketCLITheme(darkTheme = false) {
        Surface {
            PocketTechnicalSurface(
                modifier = Modifier.padding(16.dp),
                header = {
                    Text(
                        text = MainActivity.kt,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = Copy,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                content = {
                    val scroll = rememberScrollState()
                    Text(
                        text = fun main() {\n println("Hello, PocketCLI!")\n},
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = LocalPocketCodeScheme.current.text,
                        modifier = Modifier.horizontalScroll(scroll)
                    )
                }
            )
        }
    }
}

@Preview(name = PocketTechnicalSurface Dark)
@Composable
fun PocketTechnicalSurfaceDarkPreview() {
    PocketCLITheme(darkTheme = true) {
        Surface {
            PocketTechnicalSurface(
                modifier = Modifier.padding(16.dp),
                header = {
                    Text(
                        text = terminal - opencode serve,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                content = {
                    Text(
                        text = [Supervisor] Listening on 127.0.0.1:4096\n[Info] Ready for connections,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = LocalPocketCodeScheme.current.text
                    )
                }
            )
        }
    }
}