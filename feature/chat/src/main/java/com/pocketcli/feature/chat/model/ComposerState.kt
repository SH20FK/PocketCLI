package com.pocketcli.feature.chat.model

import androidx.compose.runtime.Immutable

sealed interface ComposerMode {
    data object Empty : ComposerMode
    data object Typing : ComposerMode
    data object WithAttachments : ComposerMode
    data object Sending : ComposerMode
    data object Running : ComposerMode
    data object AwaitingPermission : ComposerMode
    data object OfflineDraft : ComposerMode
}

@Immutable
data class ComposerState(
    val text: String = "",
 val attachments: List<String> = emptyList(),
 val mode: ComposerMode = ComposerMode.Empty,
 val selectedModelName: String? = null,
 val isSendEnabled: Boolean = false
)