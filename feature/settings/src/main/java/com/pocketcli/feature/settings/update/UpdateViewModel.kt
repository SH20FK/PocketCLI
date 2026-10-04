package com.pocketcli.feature.settings.update

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.update.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            currentVersionName = getAppVersionName(),
            currentVersionCode = getAppVersionCode(),
            selectedChannel = UpdateChannel.BETA
        )
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        checkForUpdates()
    }

    private fun getAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun getAppVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1000000L
        }
    }

    fun setChannel(channel: UpdateChannel) {
        _uiState.update { it.copy(selectedChannel = channel) }
        checkForUpdates()
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        _uiState.update { it.copy(wifiOnly = wifiOnly) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = UpdateStatus.Checking, errorMessage = null) }
            val currentCode = _uiState.value.currentVersionCode
            val currentName = _uiState.value.currentVersionName
            val channel = _uiState.value.selectedChannel

            val result = updateRepository.checkForUpdates(currentCode, channel, currentName)
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _uiState.update { it.copy(status = UpdateStatus.Available(result.manifest)) }
                }
                is UpdateCheckResult.UpToDate -> {
                    _uiState.update { it.copy(status = UpdateStatus.UpToDate()) }
                }
                is UpdateCheckResult.NoRelease -> {
                    val msg = if (result.channel == UpdateChannel.STABLE) {
                        "Стабильных выпусков пока нет. Можно переключиться на Beta или открыть страницу релизов."
                    } else {
                        "Выпусков в канале ${result.channel.displayName} пока нет."
                    }
                    _uiState.update { it.copy(status = UpdateStatus.NoRelease(result.channel, msg)) }
                }
                is UpdateCheckResult.Error -> {
                    _uiState.update {
                        it.copy(
                            status = UpdateStatus.Error(result.message),
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun downloadUpdate(manifest: UpdateManifest) {
        viewModelScope.launch {
            val targetFile = updateRepository.getLocalApkFile(manifest.versionName)
            _uiState.update {
                it.copy(
                    status = UpdateStatus.Downloading(
                        DownloadProgress(0L, manifest.apk.size, 0f),
                        manifest
                    )
                )
            }

            try {
                updateRepository.downloadApk(manifest.apk.url, targetFile).collect { progress ->
                    if (progress.isCompleted) {
                        verifyDownloadedApk(targetFile, manifest)
                    } else {
                        _uiState.update {
                            it.copy(status = UpdateStatus.Downloading(progress, manifest))
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        status = UpdateStatus.Error(e.localizedMessage ?: "Ошибка скачивания файла"),
                        errorMessage = e.localizedMessage
                    )
                }
            }
        }
    }

    private suspend fun verifyDownloadedApk(apkFile: File, manifest: UpdateManifest) {
        _uiState.update { it.copy(status = UpdateStatus.Verifying("Проверка контрольной суммы SHA-256...", manifest)) }

        val verifyResult = updateRepository.verifyApk(
            file = apkFile,
            expectedSha256 = manifest.apk.sha256,
            expectedSize = manifest.apk.size,
            minVersionCode = _uiState.value.currentVersionCode,
            expectedPackageName = "com.pocketcli"
        )

        verifyResult.fold(
            onSuccess = {
                _uiState.update { it.copy(status = UpdateStatus.ReadyToInstall(apkFile, manifest)) }
            },
            onFailure = { error ->
                apkFile.delete()
                _uiState.update {
                    it.copy(
                        status = UpdateStatus.Error("Ошибка проверки безопасности: ${error.message}", isRecoverable = false),
                        errorMessage = error.message
                    )
                }
            }
        )
    }

    fun installApk(apkFile: File, onStartActivity: (Intent) -> Unit) {
        if (!updateRepository.canRequestPackageInstalls()) {
            _uiState.update { it.copy(status = UpdateStatus.AwaitingPermission(apkFile)) }
            return
        }

        try {
            val installIntent = updateRepository.createInstallIntent(apkFile)
            onStartActivity(installIntent)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    status = UpdateStatus.Error("Не удалось запустить установщик: ${e.message}"),
                    errorMessage = e.message
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
