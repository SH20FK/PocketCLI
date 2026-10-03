package com.pocketcli.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.components.PocketStatus
import com.pocketcli.core.ui.components.PocketStatusState
import com.pocketcli.core.ui.theme.PocketSpacing

/**
 * ChatTopBar according to section 4.2:
 * - 64dp height
 * - back button: 48x48
 * - central column: session title, subtitle with project · runtime and 8dp status glyph
 * - right actions: files (48x48) and overflow menu (48x48)
 * - model chip removed from top bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    sessionTitle: String,
    projectName: String,
    runtimeName: String,
    statusState: PocketStatusState,
    onNavigateBack: () -> Unit,
    onTitleClick: () -> Unit = {},
    onFilesClick: () -> Unit = {},
    onOverflowClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier.heightIn(min = 64.dp),
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад"
                )
            }
        },
        title = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTitleClick)
            ) {
                Text(
                    text = sessionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs)
                ) {
                    PocketStatus(state = statusState, compact = true)
                    Text(
                        text = "$projectName · $runtimeName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onFilesClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Файлы"
                )
            }
            IconButton(
                onClick = onOverflowClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Опции"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}