package com.pocketcli.feature.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pocketcli.core.model.WorkspaceWithDetails

@Composable
fun ProjectPickerSheet(
    workspaces: List<WorkspaceWithDetails>,
    selectedWorkspaceId: String?,
    onSelectWorkspace: (String?) -> Unit,
    onAddNewProject: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.pocketcli.core.ui.components.ProjectPickerSheet(
        workspaces = workspaces,
        selectedWorkspaceId = selectedWorkspaceId,
        onSelectWorkspace = onSelectWorkspace,
        onAddNewProject = onAddNewProject,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
