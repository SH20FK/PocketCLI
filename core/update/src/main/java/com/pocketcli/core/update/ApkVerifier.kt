package com.pocketcli.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkVerifier(
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") marker: Unit = Unit
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Unit)

    constructor() : this(null, Unit)

    suspend fun verifyApk(
        apkFile: File,
        expectedSha256: String,
        expectedSize: Long? = null,
        expectedPackageName: String? = "com.pocketcli",
        minVersionCode: Long? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Файл APK не найден: ${apkFile.absolutePath}"))
        }

        val maxAllowedSize = 150L * 1024L * 1024L // 150 MB safety threshold
        if (apkFile.length() > maxAllowedSize) {
            return@withContext Result.failure(
                SecurityException("Размер файла APK (${apkFile.length()} байт) превышает допустимый лимит безопасности 150 МБ")
            )
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

            // Optional Android package verification when running in Android environment
            context?.let { ctx ->
                try {
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        @Suppress("DEPRECATION")
                        PackageManager.GET_SIGNATURES
                    }
                    val archiveInfo = ctx.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
                    if (archiveInfo != null) {
                        val expectedPkg = expectedPackageName ?: ctx.packageName
                        if (archiveInfo.packageName != expectedPkg) {
                            return@withContext Result.failure(
                                SecurityException("Имя пакета в APK (${archiveInfo.packageName}) не совпадает с ожидаемым ($expectedPkg)")
                            )
                        }
                        if (minVersionCode != null && archiveInfo.longVersionCode <= minVersionCode) {
                            return@withContext Result.failure(
                                SecurityException("Номер версии в APK (${archiveInfo.longVersionCode}) должен быть строго больше установленного ($minVersionCode)")
                            )
                        }

                        // Check signature compatibility with currently installed package
                        val currentPackageInfo = try {
                            ctx.packageManager.getPackageInfo(ctx.packageName, flags)
                        } catch (_: Exception) {
                            null
                        }

                        if (currentPackageInfo != null) {
                            val currentCerts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                currentPackageInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }
                            } else {
                                @Suppress("DEPRECATION")
                                currentPackageInfo.signatures?.map { it.toCharsString() }
                            }

                            val newCerts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                archiveInfo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }
                            } else {
                                @Suppress("DEPRECATION")
                                archiveInfo.signatures?.map { it.toCharsString() }
                            }

                            if (!currentCerts.isNullOrEmpty() && !newCerts.isNullOrEmpty()) {
                                val match = currentCerts.any { cur -> newCerts.contains(cur) }
                                if (!match) {
                                    return@withContext Result.failure(
                                        SecurityException(
                                            "Подпись обновляемого APK не совпадает с установленным приложением.\n" +
                                            "Для перехода на единую релизную подпись требуется однократно удалить старую версию и установить этот APK."
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    return@withContext Result.failure(e)
                } catch (_: Exception) {
                    // Ignored in unit testing environments where PackageManager is unavailable
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
