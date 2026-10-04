package com.pocketcli.core.update

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult
    data class UpToDate(val currentVersionCode: Long) : UpdateCheckResult
    data class NoRelease(val channel: UpdateChannel) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val releaseApi: GitHubReleaseApi,
    private val updateChecker: UpdateChecker,
    private val downloader: ApkDownloader,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller
) {
    fun getUpdateCacheDirectory(): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getLocalApkFile(versionName: String): File {
        return File(getUpdateCacheDirectory(), "pocketcli-$versionName.apk")
    }

    suspend fun checkForUpdates(
        currentVersionCode: Long,
        channel: UpdateChannel = UpdateChannel.STABLE,
        currentVersionName: String? = null
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        when (val result = releaseApi.fetchLatestManifest(channel = channel)) {
            is ManifestFetchResult.Found -> {
                val manifest = result.manifest
                if (updateChecker.isUpdateAvailable(currentVersionCode, manifest, currentVersionName)) {
                    UpdateCheckResult.UpdateAvailable(manifest)
                } else {
                    UpdateCheckResult.UpToDate(currentVersionCode)
                }
            }
            is ManifestFetchResult.NoRelease -> {
                UpdateCheckResult.NoRelease(channel)
            }
            is ManifestFetchResult.MissingAsset -> {
                UpdateCheckResult.Error("В релизе ${result.tag} отсутствует файл ${result.asset}")
            }
            is ManifestFetchResult.InvalidManifest -> {
                UpdateCheckResult.Error("Манифест релиза ${result.tag} повреждён: ${result.reason}")
            }
            is ManifestFetchResult.NetworkError -> {
                UpdateCheckResult.Error("Сетевая ошибка: ${result.reason}")
            }
        }
    }

    fun downloadApk(url: String, targetFile: File): Flow<DownloadProgress> {
        return downloader.downloadApk(url, targetFile)
    }

    suspend fun verifyApk(file: File, expectedSha256: String, expectedSize: Long?): Result<Boolean> {
        return verifier.verifyApk(file, expectedSha256, expectedSize)
    }

    fun canRequestPackageInstalls(): Boolean {
        return installer.canRequestPackageInstalls()
    }

    fun createInstallIntent(file: File): Intent {
        return installer.createInstallIntent(file)
    }

    fun clearCache() {
        val dir = getUpdateCacheDirectory()
        dir.deleteRecursively()
    }
}
