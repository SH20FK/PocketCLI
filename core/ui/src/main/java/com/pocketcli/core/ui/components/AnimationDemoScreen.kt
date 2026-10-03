package com.pocketcli.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketCLITheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationDemoScreen(
    modifier: Modifier = Modifier
) {
    var isToggled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталог анимаций PocketCLI") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Morphing Icons
            Text(text = "1. Морфинг иконок (PocketAnimatedIcon)", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { isToggled = !isToggled }) {
                Text(if (isToggled) "Состояние B" else "Состояние A")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PocketAnimatedIcon(
                        state = if (isToggled) IconState.STOP else IconState.SEND,
                        contentDescription = "Send/Stop"
                    )
                    Text("Send / Stop", style = MaterialTheme.typography.labelSmall)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PocketAnimatedIcon(
                        state = if (isToggled) IconState.STOP else IconState.PLAY,
                        contentDescription = "Play/Stop"
                    )
                    Text("Play / Stop", style = MaterialTheme.typography.labelSmall)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PocketAnimatedIcon(
                        state = if (isToggled) IconState.COLLAPSE else IconState.EXPAND,
                        contentDescription = "Expand/Collapse"
                    )
                    Text("Expand / Collapse", style = MaterialTheme.typography.labelSmall)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PocketAnimatedIcon(
                        state = if (isToggled) IconState.SEND else IconState.MIC,
                        contentDescription = "Mic/Send"
                    )
                    Text("Mic / Send", style = MaterialTheme.typography.labelSmall)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PocketAnimatedIcon(
                        state = if (isToggled) IconState.CHECK else IconState.SYNC,
                        contentDescription = "Sync/Check"
                    )
                    Text("Sync / Check", style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider()

            // Section 2: Status Pills
            Text(text = "2. Статусные пилюли (PocketStatusPill)", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketStatusPill(state = AgentActivityState.READY)
                PocketStatusPill(state = AgentActivityState.THINKING)
                PocketStatusPill(state = AgentActivityState.EXECUTING)
                PocketStatusPill(state = AgentActivityState.AWAITING_PERMISSION)
                PocketStatusPill(state = AgentActivityState.OFFLINE)
            }

            HorizontalDivider()

            // Section 3: Animation Illustrations
            Text(text = "3. Контейнеры иллюстраций (PocketAnimationContainer)", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PocketAnimationContainer(type = PocketAnimationType.LOADING)
                PocketAnimationContainer(type = PocketAnimationType.THINKING)
                PocketAnimationContainer(type = PocketAnimationType.SUCCESS)
                PocketAnimationContainer(type = PocketAnimationType.ERROR)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PocketAnimationContainer(type = PocketAnimationType.EMPTY_CHATS)
                PocketAnimationContainer(type = PocketAnimationType.EMPTY_PROJECTS)
                PocketAnimationContainer(type = PocketAnimationType.CLOUD_SYNC)
            }
        }
    }
}

@Preview(name = "Animation Demo Light", showBackground = true)
@Composable
fun AnimationDemoScreenPreview() {
    PocketCLITheme(darkTheme = false) {
        AnimationDemoScreen()
    }
}

@Preview(name = "Animation Demo Dark", showBackground = true)
@Composable
fun AnimationDemoScreenDarkPreview() {
    PocketCLITheme(darkTheme = true) {
        AnimationDemoScreen()
    }
}
