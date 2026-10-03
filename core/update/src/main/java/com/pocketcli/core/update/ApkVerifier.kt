package com.pocketcli.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkVerifier @Inject constructor() {

    suspend fun verifyApk(
        apkFile: File,
        expectedSha256: String,
        expectedSize: Long? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Файл APK не найден: ${apkFile.absolutePath}"))
        }

        if (expectedSize != null && expectedSize > 0 && apkFile.length() != expectedSize) {
            return@withContext Result.failure(
                IllegalStateException("Размер файла не совпадает: ожидалось $expectedSize, получено ${apkFile.length()}")
            )
        }

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(apkFile).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val calculatedSha = digest.digest().joinToString("") { "%02x".format(it) }

            if (!calculatedSha.equals(expectedSha256.trim(), ignoreCase = true)) {
                return@withContext Result.failure(
                    SecurityException("Контрольная сумма SHA-256 не совпадает!\nОжидалось: $expectedSha256\nПолучено: $calculatedSha")
                )
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
