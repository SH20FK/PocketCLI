package com.pocketcli.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketSpacing

data class ActiveSessionUi(
    val sessionId: String,
    val projectTitle: String,
    val currentAction: String,
    val elapsedTime: String = "",
    val isRunning: Boolean = true
)

/**
 * PocketScreenScaffold provides standard top bar, reserved space for active session bar,
 * snackbar host, and proper content padding without overlapping active session surfaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    activeSession: ActiveSessionUi? = null,
    onActiveSessionClick: (() -> Unit)? = null,
    onActiveSessionStop: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { navigationIcon?.invoke() },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val bottomReserved = if (activeSession != null) 72.dp + PocketSpacing.sm else 0.dp
            val adjustedPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = innerPadding.calculateBottomPadding() + bottomReserved
            )

            content(adjustedPadding)

            if (activeSession != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding() + PocketSpacing.xs)
                        .padding(horizontal = PocketSpacing.md)
                ) {
                    ActiveSessionBar(
                        projectTitle = activeSession.projectTitle,
                        currentAction = activeSession.currentAction,
                        elapsedTime = activeSession.elapsedTime,
                        onClick = { onActiveSessionClick?.invoke() },
                        onStop = { onActiveSessionStop?.invoke() }
                    )
                }
            }
        }
    }
}
