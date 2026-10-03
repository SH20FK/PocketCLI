package com.pocketcli.feature.settings.update

import com.pocketcli.core.update.DownloadProgress
import com.pocketcli.core.update.UpdateChannel
import com.pocketcli.core.update.UpdateManifest
import java.io.File

sealed interface UpdateStatus {
    object Idle : UpdateStatus
    object Checking : UpdateStatus
    data class UpToDate(val checkedAt: Long = System.currentTimeMillis()) : UpdateStatus
    data class Available(val manifest: UpdateManifest) : UpdateStatus
    data class Downloading(val progress: DownloadProgress, val manifest: UpdateManifest) : UpdateStatus
    data class Verifying(val step: String, val manifest: UpdateManifest) : UpdateStatus
    data class ReadyToInstall(val apkFile: File, val manifest: UpdateManifest) : UpdateStatus
    data class AwaitingPermission(val apkFile: File) : UpdateStatus
    data class Error(val message: String, val isRecoverable: Boolean = true) : UpdateStatus
}

data class UpdateUiState(
    val currentVersionName: String = "1.0.0",
    val currentVersionCode: Long = 1000000L,
    val selectedChannel: UpdateChannel = UpdateChannel.STABLE,
    val wifiOnly: Boolean = true,
    val autoCheck: Boolean = true,
    val status: UpdateStatus = UpdateStatus.Idle,
    val errorMessage: String? = null
)
