package com.pocketcli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import com.pocketcli.feature.chat.ChatScreen
import com.pocketcli.feature.chat.ChatViewModel
import com.pocketcli.feature.projects.ProjectsScreen
import com.pocketcli.feature.projects.ProjectsViewModel
import com.pocketcli.feature.sessions.SessionsScreen
import com.pocketcli.feature.sessions.SessionsViewModel
import com.pocketcli.feature.settings.SettingsScreen
import com.pocketcli.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable object SessionsRoute
@Serializable data class ChatRoute(val sessionId: String, val profileId: String = "")
@Serializable object ProjectsRoute
@Serializable object SettingsRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var connectionManager: ActiveConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PocketCLITheme {
                val navController = rememberNavController()
                var selectedTab by remember { mutableIntStateOf(0) }

                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        item(
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Sessions") },
                            label = { Text("Sessions") },
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                navController.navigate(SessionsRoute) {
                                    popUpTo(SessionsRoute) { inclusive = true }
                                }
                            }
                        )
                        item(
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Projects") },
                            label = { Text("Projects") },
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                navController.navigate(ProjectsRoute) {
                                    popUpTo(SessionsRoute)
                                }
                            }
                        )
                        item(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            selected = selectedTab == 2,
                            onClick = {
                                selectedTab = 2
                                navController.navigate(SettingsRoute) {
                                    popUpTo(SessionsRoute)
                                }
                            }
                        )
                    }
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
                                    selectedTab = 0
                                    navController.navigate(SessionsRoute) {
                                        popUpTo(SessionsRoute) { inclusive = true }
                                    }
                                },
                                onStartSessionForProject = { workspaceId ->
                                    selectedTab = 0
                                    navController.navigate(SessionsRoute) {
                                        popUpTo(SessionsRoute) { inclusive = true }
                                    }
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
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
