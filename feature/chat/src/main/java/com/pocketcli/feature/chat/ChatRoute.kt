package com.pocketcli.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ChatRoute(
    sessionId: String,
    profileId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId, profileId) {
        viewModel.initialize(sessionId = sessionId, profileId = profileId)
    }

    ChatScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}