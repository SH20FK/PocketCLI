package com.pocketcli.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.WorkspaceGitStatus
import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.core.ui.theme.SemanticWarning
import com.pocketcli.core.ui.theme.ToolRunningColor
import com.pocketcli.core.ui.theme.ToolSuccessColor
import java.text.SimpleDateFormat
import java.util.*

/**
 * ProjectCard adhering strictly to POCKETCLI_UI_IMPLEMENTATION_PACK.md:
 * - 3-tier hierarchy:
 *   1. title + overflow menu
 *   2. path/branch (e.g. main · 3 изменения)
 *   3. status summary + last activity (e.g. Локальный runtime · 2 активные сессии · 5 мин)
 * - 0dp elevation, PocketShapes.container (20dp)
 * - Static icon/color for status, NO infinite pulse animation
 * - 48dp minimum touch targets
 */
@Composable
fun ProjectCard(
    item: WorkspaceWithDetails,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onStartSession: (() -> Unit)? = null,
    isActive: Boolean = false,
    activeActionText: String? = null
) {
    val workspace = item.workspace
    val git = item.gitStatus
    var showMenu by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val formattedTime = remember(workspace.lastOpenedAt) { dateFormat.format(Date(workspace.lastOpenedAt)) }

    Card(
        shape = PocketShapes.container,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(PocketShapes.container)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PocketSpacing.md)
        ) {
            // Tier 1: Avatar/Icon, Title & Overflow Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = PocketShapes.compact,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (workspace.sourceType == WorkspaceSourceType.CLONED) {
                                    Icons.Default.CloudSync
                                } else {
                                    Icons.Default.Folder
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(PocketSpacing.sm))

                    Text(
                        text = workspace.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Опции"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (workspace.archived) "Восстановить" else "В архив") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (workspace.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleArchive()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить проект") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(PocketSpacing.xxs))

            // Tier 2: Path & Branch / Changes (e.g. main · 3 изменения)
            val branchText = git.branch.takeIf { !it.isNullOrBlank() }
            val dirtyText = if (git.isDirty) "изменения" else "чисто"
            val tier2Text = buildString {
                if (branchText != null) {
                    append(branchText)
                    append(" · ")
                }
                val subPath = if (!workspace.remoteUrl.isNullOrBlank()) {
                    workspace.remoteUrl
                } else {
                    workspace.localPath.substringAfterLast('/')
                }
                append(subPath)
                if (git.isGitRepo) {
                    append(" · ")
                    append(dirtyText)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (git.isGitRepo) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (git.isDirty) SemanticWarning else ToolSuccessColor)
                    )
                    Spacer(modifier = Modifier.width(PocketSpacing.xs))
                }
                Text(
                    text = tier2Text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(PocketSpacing.xs))

            // Tier 3: Status summary + last activity (e.g. Локальный runtime · 2 активные сессии · 5 мин)
            val runtimeText = if (workspace.sourceType == WorkspaceSourceType.CLONED) "Git" else "Локальный runtime"
            val sessionText = if (item.sessionCount > 0) "${item.sessionCount} сессий" else "нет сессий"
            val tier3Text = if (isActive && !activeActionText.isNullOrBlank()) {
                "$runtimeText · $activeActionText"
            } else {
                "$runtimeText · $sessionText · $formattedTime"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isActive) {
                        // Static active dot (no infinite pulse)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ToolRunningColor)
                        )
                        Spacer(modifier = Modifier.width(PocketSpacing.xs))
                    }
                    Text(
                        text = tier3Text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive) ToolRunningColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isActive) {
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Открыть")
                    }
                }
            }
        }
    }
}

@Preview(name = "ProjectCard Light")
@Composable
fun ProjectCardPreview() {
    PocketCLITheme(darkTheme = false) {
        Surface {
            ProjectCard(
                item = WorkspaceWithDetails(
                    workspace = Workspace(
                        id = "1",
                        profileId = "default",
                        displayName = "pocketcli-android",
                        localPath = "/workspace/pocketcli",
                        sourceType = WorkspaceSourceType.CREATED,
                        lastOpenedAt = System.currentTimeMillis()
                    ),
                    gitStatus = WorkspaceGitStatus(isGitRepo = true, branch = "main", isDirty = true),
                    sessionCount = 3
                ),
                onClick = {},
                onToggleArchive = {},
                onDelete = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
