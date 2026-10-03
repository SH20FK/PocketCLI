package com.pocketcli.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.PermissionOption
import com.pocketcli.core.model.ToolCall
import com.pocketcli.core.ui.theme.PocketShapes
import com.pocketcli.core.ui.theme.PocketSpacing
import com.pocketcli.feature.chat.model.ChatNode
import kotlinx.coroutines.launch

/**
 * ChatTimeline according to section 4.2 & 13:
 * - LazyColumn with stable keys and contentTypes
 * - Horizontal padding 16 dp
 * - 24 dp between user and agent turns, 8 dp inside compound agent turn
 * - Autoscroll triggered ONLY when isAtBottom == true
 * - Floating К новым сообщениям pill appears when user scrolls up
 */
@Composable
fun ChatTimeline(
    nodes: List<ChatNode>,
    onOpenToolDetails: (ToolCall) -> Unit,
    onReplyPermission: (requestId: String, option: PermissionOption) -> Unit,
    onOpenDiff: (filePath: String, diffContent: String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val coroutineScope = rememberCoroutineScope()

    // Smart autoscroll detection
    val isAtBottom by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) true
            else {
                val lastVisibleIndex = visibleItems.last().index
                lastVisibleIndex >= (listState.layoutInfo.totalItemsCount - 2).coerceAtLeast(0)
            }
        }
    }

    // Scroll to bottom when new messages arrive and user was already at bottom
    LaunchedEffect(nodes.size) {
        if (isAtBottom && nodes.isNotEmpty()) {
            listState.animateScrollToItem(nodes.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = PocketSpacing.md,
                vertical = PocketSpacing.md
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = nodes,
                key = { it.id },
                contentType = { it::class.java.simpleName }
            ) { node ->
                val spaceBelow = when (node) {
                    is ChatNode.UserNode -> PocketSpacing.lg
                    is ChatNode.AssistantNode -> PocketSpacing.md
                    is ChatNode.ToolNode -> PocketSpacing.xs
                    is ChatNode.PermissionNode -> PocketSpacing.sm
                    is ChatNode.DiffNode -> PocketSpacing.sm
                    is ChatNode.StreamingTailNode -> PocketSpacing.xs
                    is ChatNode.TodoNode -> PocketSpacing.sm
                }

                when (node) {
                    is ChatNode.UserNode -> {
                        UserMessage(
                            text = node.text,
                            timestamp = node.timestamp
                        )
                    }
                    is ChatNode.AssistantNode -> {
                        AssistantMessage(
                            message = node.message,
                            isStreaming = node.isStreaming
                        )
                    }
                    is ChatNode.ToolNode -> {
                        ToolTimelineItem(
                            toolCall = node.toolCall,
                            onClick = { onOpenToolDetails(node.toolCall) }
                        )
                    }
                    is ChatNode.PermissionNode -> {
                        PermissionTimelineItem(
                            requestId = node.request.requestId,
                            title = node.request.title,
                            onReply = onReplyPermission
                        )
                    }
                    is ChatNode.DiffNode -> {
                        DiffSummaryItem(
                            filePath = node.filePath,
                            onOpenDiff = { onOpenDiff(node.filePath, node.diffContent) },
                            additions = node.additions,
                            deletions = node.deletions
                        )
                    }
                    is ChatNode.StreamingTailNode -> {
                        StreamingTail(tail = node.tail)
                    }
                    is ChatNode.TodoNode -> {
                        TodoCard(todos = node.todos)
                    }
                }

                Spacer(modifier = Modifier.height(spaceBelow))
            }
        }

        // Floating К новым сообщениям pill
        AnimatedVisibility(
            visible = !isAtBottom && nodes.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { 40 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { 40 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PocketSpacing.md)
        ) {
            Surface(
                shape = PocketShapes.action,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .clip(PocketShapes.action)
                    .clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem(nodes.size - 1)
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = PocketSpacing.md, vertical = PocketSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(PocketSpacing.xs))
                    Text(
                        text = "К новым сообщениям",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}