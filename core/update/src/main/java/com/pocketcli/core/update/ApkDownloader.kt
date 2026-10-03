package com.pocketcli.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progressFraction: Float,
    val speedBytesPerSec: Long = 0L,
    val isCompleted: Boolean = false
)

@Singleton
class ApkDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    fun validateDownloadUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            scheme == "https" && (
                host == "github.com" ||
                host?.endsWith(".github.com") == true ||
                host == "objects.githubusercontent.com" ||
                host == "raw.githubusercontent.com"
            )
        } catch (_: Exception) {
            false
        }
    }

    fun downloadApk(
        downloadUrl: String,
        destinationFile: File
    ): Flow<DownloadProgress> = flow {
        if (!validateDownloadUrl(downloadUrl)) {
            throw SecurityException("Недопустимый URL для скачивания обновления: $downloadUrl. Разрешены только HTTPS URL на github.com и objects.githubusercontent.com")
        }

        destinationFile.parentFile?.mkdirs()
        val existingBytes = if (destinationFile.exists()) destinationFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "PocketCLI-Android-Updater")

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful && response.code != 206) {
            throw IOException("HTTP ${response.code}: Не удалось начать скачивание APK")
        }

        val body = response.body ?: throw IOException("Пустое тело ответа")
        val isAppend = response.code == 206
        val contentLength = body.contentLength()
        val totalBytes = if (isAppend) existingBytes + contentLength else contentLength

        val outputStream = FileOutputStream(destinationFile, isAppend)
        val buffer = ByteArray(8192)
        var downloadedBytes = if (isAppend) existingBytes else 0L
        var bytesRead: Int
        var lastTime = System.currentTimeMillis()
        var bytesSinceLastTime = 0L
        var currentSpeed = 0L

        outputStream.use { fos ->
            body.byteStream().use { inputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDelta = now - lastTime
                    if (timeDelta >= 500) {
                        currentSpeed = (bytesSinceLastTime * 1000) / timeDelta
                        lastTime = now
                        bytesSinceLastTime = 0L

                        val fraction = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        emit(
                            DownloadProgress(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                progressFraction = fraction,
                                speedBytesPerSec = currentSpeed
                            )
                        )
                    }
                }
            }
        }

        emit(
            DownloadProgress(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                progressFraction = 1f,
                speedBytesPerSec = 0L,
                isCompleted = true
            )
        )
    }.flowOn(Dispatchers.IO)
}
