package com.pocketcli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.core.ui.components.ActiveSessionBar
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import com.pocketcli.data.opencode.repository.AgentSessionRepository
import com.pocketcli.feature.chat.ChatScreen
import com.pocketcli.feature.chat.ChatViewModel
import com.pocketcli.feature.projects.ProjectDetailScreen
import com.pocketcli.feature.projects.ProjectsScreen
import com.pocketcli.feature.projects.ProjectsViewModel
import com.pocketcli.feature.sessions.SessionsScreen
import com.pocketcli.feature.sessions.SessionsViewModel
import com.pocketcli.feature.settings.SettingsScreen
import com.pocketcli.feature.settings.SettingsViewModel
import com.pocketcli.feature.settings.update.UpdateScreen
import com.pocketcli.feature.settings.update.UpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable object SessionsRoute
@Serializable data class ChatRoute(val sessionId: String, val profileId: String = "")
@Serializable object ProjectsRoute
@Serializable data class ProjectDetailRoute(val workspaceId: String)
@Serializable object SettingsRoute
@Serializable object UpdateRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var connectionManager: ActiveConnectionManager

    @Inject
    lateinit var sessionRepository: AgentSessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketCLITheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val isDetailRoute = currentDestination?.let { dest ->
                    dest.hasRoute<ChatRoute>() ||
                    dest.hasRoute<ProjectDetailRoute>() ||
                    dest.hasRoute<UpdateRoute>() ||
                    dest.route?.contains("ChatRoute") == true ||
                    dest.route?.contains("ProjectDetailRoute") == true ||
                    dest.route?.contains("UpdateRoute") == true
                } ?: false

                val selectedTab = when {
                    currentDestination?.hasRoute<ProjectsRoute>() == true -> 1
                    currentDestination?.hasRoute<SettingsRoute>() == true -> 2
                    else -> 0
                }

                val activeSession by sessionRepository.getActiveSessionInfo().collectAsState(initial = null)
                val coroutineScope = rememberCoroutineScope()

                NavigationSuiteScaffold(
                    layoutType = if (isDetailRoute) {
                        NavigationSuiteType.None
                    } else {
                        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
                    },
                    navigationSuiteItems = {
                        item(
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Чаты") },
                            label = { Text("Чаты") },
                            selected = selectedTab == 0,
                            onClick = {
                                navController.navigate(SessionsRoute) {
                                    popUpTo(SessionsRoute) { inclusive = true }
                                }
                            }
                        )
                        item(
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Проекты") },
                            label = { Text("Проекты") },
                            selected = selectedTab == 1,
                            onClick = {
                                navController.navigate(ProjectsRoute) {
                                    popUpTo(SessionsRoute)
                                }
                            }
                        )
                        item(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                            label = { Text("Настройки") },
                            selected = selectedTab == 2,
                            onClick = {
                                navController.navigate(SettingsRoute) {
                                    popUpTo(SessionsRoute)
                                }
                            }
                        )
                    }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = SessionsRoute,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable<SessionsRoute> {
                                    val viewModel: SessionsViewModel = hiltViewModel()
                                    SessionsScreen(
                                        viewModel = viewModel,
                                        onSessionClick = { sessionId, profileId ->
                                            navController.navigate(ChatRoute(sessionId = sessionId, profileId = profileId))
                                        }
                                    )
                                }

                                composable<ProjectsRoute> {
                                    val viewModel: ProjectsViewModel = hiltViewModel()
                                    ProjectsScreen(
                                        viewModel = viewModel,
                                        onProjectClick = { workspaceId ->
                                            navController.navigate(ProjectDetailRoute(workspaceId))
                                        },
                                        onStartSessionForProject = { workspaceId ->
                                            navController.navigate(SessionsRoute) {
                                                popUpTo(SessionsRoute) { inclusive = true }
                                            }
                                        }
                                    )
                                }

                                composable<ProjectDetailRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<ProjectDetailRoute>()
                                    val projectsViewModel: ProjectsViewModel = hiltViewModel()
                                    val uiState by projectsViewModel.uiState.collectAsState()
                                    val item = uiState.workspaces.find { it.workspace.id == route.workspaceId }
                                        ?: WorkspaceWithDetails(
                                            workspace = Workspace(
                                                id = route.workspaceId,
                                                profileId = uiState.activeProfileId,
                                                displayName = "Проект",
                                                localPath = ""
                                            )
                                        )

                                    ProjectDetailScreen(
                                        workspaceItem = item,
                                        onNavigateBack = { navController.popBackStack() },
                                        onStartChat = { wsId ->
                                            navController.navigate(SessionsRoute) {
                                                popUpTo(SessionsRoute) { inclusive = true }
                                            }
                                        },
                                        onDeleteProject = { wsId ->
                                            projectsViewModel.deleteWorkspace(wsId)
                                        }
                                    )
                                }

                                composable<ChatRoute> { backStackEntry ->
                                    val route = backStackEntry.toRoute<ChatRoute>()
                                    val viewModel: ChatViewModel = hiltViewModel()

                                    LaunchedEffect(route.sessionId, route.profileId) {
                                        viewModel.initialize(sessionId = route.sessionId, profileId = route.profileId)
                                    }

                                    ChatScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable<SettingsRoute> {
                                    val viewModel: SettingsViewModel = hiltViewModel()
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateToUpdate = { navController.navigate(UpdateRoute) }
                                    )
                                }

                                composable<UpdateRoute> {
                                    val updateViewModel: UpdateViewModel = hiltViewModel()
                                    UpdateScreen(
                                        viewModel = updateViewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }

                        if (!isDetailRoute && activeSession != null) {
                            ActiveSessionBar(
                                sessionInfo = activeSession,
                                onOpenSession = { sessionId, profileId ->
                                    navController.navigate(ChatRoute(sessionId = sessionId, profileId = profileId))
                                },
                                onStopSession = { sessionId ->
                                    coroutineScope.launch {
                                        connectionManager.getAdapter()?.cancel(sessionId)
                                        sessionRepository.flushInFlightToDb(sessionId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
