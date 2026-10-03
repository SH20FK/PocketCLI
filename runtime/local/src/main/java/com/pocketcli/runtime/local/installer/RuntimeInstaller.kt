package com.pocketcli.runtime.local.installer

import android.content.Context
import android.os.Build
import com.pocketcli.runtime.local.manifest.ArtifactInfo
import com.pocketcli.runtime.local.manifest.ManifestParser
import com.pocketcli.runtime.local.proot.ProotEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InstallState {
    object NotInstalled : InstallState
    object CheckingPrerequisites : InstallState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val currentArtifact: String
    ) : InstallState
    data class Verifying(val artifact: String) : InstallState
    data class Extracting(
        val currentFile: Int,
        val totalBytesExtracted: Long,
        val artifact: String
    ) : InstallState
    data class Configuring(val step: String) : InstallState
    data class Ready(val version: String) : InstallState
    data class Failed(val error: String, val canRetry: Boolean) : InstallState
}

@Singleton
open class RuntimeInstaller(
    private val prootEnvironment: ProotEnvironment,
    private val manifestParser: ManifestParser,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val filesDirProvider: () -> File,
    private val supportedAbisProvider: () -> Array<String> = { Build.SUPPORTED_ABIS },
    private val freeSpaceProvider: () -> Long = { filesDirProvider().freeSpace },
    private val manifestContentProvider: () -> String
) {
    companion object {
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Inject
    constructor(
        @ApplicationContext context: Context,
        prootEnvironment: ProotEnvironment,
        manifestParser: ManifestParser
    ) : this(
        prootEnvironment = prootEnvironment,
        manifestParser = manifestParser,
        okHttpClient = defaultOkHttpClient(),
        ioDispatcher = Dispatchers.IO,
        filesDirProvider = { context.filesDir },
        supportedAbisProvider = { Build.SUPPORTED_ABIS },
        freeSpaceProvider = { context.filesDir.freeSpace },
        manifestContentProvider = {
            context.assets.open("local-runtime-manifest.json").bufferedReader().use { it.readText() }
        }
    )

    private val _state = MutableStateFlow<InstallState>(InstallState.NotInstalled)
    open val state: StateFlow<InstallState> = _state.asStateFlow()

    private var activeJob: Job? = null

    val runtimeBaseDir: File
        get() = File(filesDirProvider(), "runtime")

    val rootfsDir: File
        get() = File(runtimeBaseDir, "rootfs")

    val stagingDir: File
        get() = File(runtimeBaseDir, "staging")

    val downloadDir: File
        get() = File(runtimeBaseDir, "download")

    val markerFile: File
        get() = File(rootfsDir, ".pocketcli_ready")

    init {
        checkInstalledStatus()
    }

    fun checkInstalledStatus() {
        if (isInstalled()) {
            val version = getInstalledVersion() ?: "unknown"
            _state.value = InstallState.Ready(version)
        } else {
            _state.value = InstallState.NotInstalled
        }
    }

    open fun isInstalled(): Boolean {
        val ready = markerFile.exists()
        val opencode = File(rootfsDir, "usr/bin/opencode").exists()
        return ready && opencode
    }

    fun getInstalledVersion(): String? {
        if (!markerFile.exists()) return null
        return try {
            val text = markerFile.readText().trim()
            if (text.startsWith("{")) {
                Regex("\"runtimeVersion\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            } else {
                text.ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun resolveArch(abis: Array<String>): String? {
        for (abi in abis) {
            if (abi.startsWith("arm64") || abi.equals("aarch64", ignoreCase = true)) {
                return "aarch64"
            }
            if (abi.contains("x86_64") || abi.equals("amd64", ignoreCase = true)) {
                return "x86_64"
            }
        }
        return null
    }

    open suspend fun install(force: Boolean = false): Result<Unit> = withContext(ioDispatcher) {
        if (!force && isInstalled()) {
            val version = getInstalledVersion() ?: "unknown"
            _state.value = InstallState.Ready(version)
            return@withContext Result.success(Unit)
        }

        activeJob = coroutineContext[Job]

        try {
            _state.value = InstallState.CheckingPrerequisites

            // 1. PRoot environment check
            if (!prootEnvironment.isProotAvailable()) {
                val err = "Бинарники PRoot не найдены в директории: ${prootEnvironment.nativeLibraryDir.absolutePath}"
                _state.value = InstallState.Failed(err, canRetry = false)
                return@withContext Result.failure(IllegalStateException(err))
            }

            // 2. ABI check
            val abis = supportedAbisProvider()
            val arch = resolveArch(abis)
            if (arch == null) {
                val err = "Архитектура устройства (${abis.firstOrNull() ?: "unknown"}) не поддерживается. Требуется arm64-v8a или x86_64."
                _state.value = InstallState.Failed(err, canRetry = false)
                return@withContext Result.failure(IllegalStateException(err))
            }

            // 3. Manifest check
            val manifestJson = manifestContentProvider()
            val manifest = manifestParser.parse(manifestJson).getOrElse { e ->
                val err = "Ошибка чтения манифеста рантайма: ${e.message}"
                _state.value = InstallState.Failed(err, canRetry = false)
                return@withContext Result.failure(e)
            }

            val archArtifacts = manifest.artifacts[arch]
            if (archArtifacts == null) {
                val err = "В манифесте нет артефактов для архитектуры $arch"
                _state.value = InstallState.Failed(err, canRetry = false)
                return@withContext Result.failure(IllegalStateException(err))
            }

            // 4. Free space check (minimum 350 MB)
            val freeBytes = freeSpaceProvider()
            val requiredBytes = 350L * 1024L * 1024L
            if (freeBytes < requiredBytes) {
                val freeMb = freeBytes / (1024 * 1024)
                val err = "Недостаточно свободного места. Требуется минимум 350 МБ, доступно: $freeMb МБ"
                _state.value = InstallState.Failed(err, canRetry = true)
                return@withContext Result.failure(IllegalStateException(err))
            }

            downloadDir.mkdirs()

            // 5. Download rootfs
            val rootfsFile = File(downloadDir, "alpine-minirootfs-$arch-${manifest.alpineVersion}.tar.gz")
            downloadAndVerifyArtifact(archArtifacts.rootfs, "Alpine Linux Rootfs", rootfsFile)

            // 6. Download opencode
            val opencodeFile = File(downloadDir, "opencode-$arch-${manifest.runtimeVersion}.tgz")
            downloadAndVerifyArtifact(archArtifacts.opencode, "OpenCode Server", opencodeFile)

            // 7. Extract to staging
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            stagingDir.mkdirs()

            _state.value = InstallState.Extracting(0, 0, "Alpine Linux Rootfs")
            TarExtractor.extractTarGz(rootfsFile.inputStream(), stagingDir) { count, bytes ->
                _state.value = InstallState.Extracting(count, bytes, "Alpine Linux Rootfs")
            }

            _state.value = InstallState.Extracting(0, 0, "OpenCode Server")
            val opencodeTempDir = File(stagingDir, "tmp_opencode_extract")
            opencodeTempDir.mkdirs()
            try {
                TarExtractor.extractTarGz(opencodeFile.inputStream(), opencodeTempDir) { count, bytes ->
                    _state.value = InstallState.Extracting(count, bytes, "OpenCode Server")
                }

                val binary = findFileByName(opencodeTempDir, "opencode")
                    ?: throw IllegalStateException("Бинарный файл 'opencode' не найден в архиве")

                val targetBin = File(stagingDir, "usr/bin/opencode")
                targetBin.parentFile?.mkdirs()
                binary.copyTo(targetBin, overwrite = true)
                targetBin.setExecutable(true, false)

                val localBin = File(stagingDir, "usr/local/bin/opencode")
                localBin.parentFile?.mkdirs()
                try {
                    Files.deleteIfExists(localBin.toPath())
                    Files.createSymbolicLink(localBin.toPath(), Paths.get("../../bin/opencode"))
                } catch (_: Exception) {
                    targetBin.copyTo(localBin, overwrite = true)
                    localBin.setExecutable(true, false)
                }
            } finally {
                opencodeTempDir.deleteRecursively()
            }

            // 8. Configure Rootfs
            _state.value = InstallState.Configuring("Настройка DNS и сетевых параметров")
            configureRootfs(stagingDir)

            // 9. Atomic swap
            _state.value = InstallState.Configuring("Активация рантайма")
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
            }
            val renamed = stagingDir.renameTo(rootfsDir)
            if (!renamed) {
                copyDirectoryRecursively(stagingDir, rootfsDir)
                stagingDir.deleteRecursively()
            }

            // 10. Marker
            writeMarker(rootfsDir, manifest.runtimeVersion, manifest.alpineVersion)

            _state.value = InstallState.Ready(manifest.runtimeVersion)
            Result.success(Unit)
        } catch (e: CancellationException) {
            _state.value = InstallState.Failed("Установка отменена", canRetry = true)
            throw e
        } catch (e: Exception) {
            val err = when (e) {
                is java.net.SocketTimeoutException -> "Превышено время ожидания сети (${e.message ?: "read timed out"}). Проверьте подключение к сети."
                is java.net.UnknownHostException -> "Не удалось разрешить адрес сервера (${e.message}). Проверьте подключение к сети."
                else -> e.localizedMessage ?: e.message ?: "Ошибка при установке рантайма"
            }
            _state.value = InstallState.Failed(err, canRetry = true)
            Result.failure(e)
        } finally {
            activeJob = null
        }
    }

    suspend fun repair(): Result<Unit> {
        return install(force = true)
    }

    open suspend fun uninstall(deleteCachedDownloads: Boolean = false): Result<Unit> = withContext(ioDispatcher) {
        cancel()
        if (rootfsDir.exists()) {
            rootfsDir.deleteRecursively()
        }
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
        if (deleteCachedDownloads && downloadDir.exists()) {
            downloadDir.deleteRecursively()
        }
        _state.value = InstallState.NotInstalled
        Result.success(Unit)
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    private suspend fun downloadAndVerifyArtifact(
        artifact: ArtifactInfo,
        name: String,
        targetFile: File
    ) {
        // If file already exists and size matches, check hash
        if (targetFile.exists() && targetFile.length() == artifact.sizeBytes) {
            _state.value = InstallState.Verifying(name)
            val hash = computeSha256(targetFile)
            if (hash.equals(artifact.sha256, ignoreCase = true)) {
                return
            } else {
                targetFile.delete()
            }
        }

        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val candidateUrls = artifact.allUrls
        var lastException: Exception? = null
        var downloadSuccess = false

        for (url in candidateUrls) {
            try {
                downloadFromUrl(url, artifact, name, partFile)
                downloadSuccess = true
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                // If sha256 mismatch or corrupted data, delete partFile so the next mirror starts clean
                if (e is IllegalStateException) {
                    partFile.delete()
                }
            }
        }

        if (!downloadSuccess) {
            throw lastException ?: IOException("Не удалось загрузить $name со всех доступных адресов")
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        val renamed = partFile.renameTo(targetFile)
        if (!renamed) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }
    }

    private suspend fun downloadFromUrl(
        url: String,
        artifact: ArtifactInfo,
        name: String,
        partFile: File
    ) {
        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        if (existingBytes > artifact.sizeBytes) {
            partFile.delete()
            existingBytes = 0L
        }

        _state.value = InstallState.Downloading(
            progress = if (artifact.sizeBytes > 0) (existingBytes.toFloat() / artifact.sizeBytes).coerceIn(0f, 1f) else 0f,
            downloadedBytes = existingBytes,
            totalBytes = artifact.sizeBytes,
            currentArtifact = name
        )

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0L) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        try {
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    partFile.delete()
                    val restartResponse = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                    try {
                        writeResponseBody(restartResponse, partFile, false, 0L, artifact, name)
                    } finally {
                        restartResponse.close()
                    }
                } else {
                    throw IOException("Ошибка загрузки $name с $url: HTTP ${response.code} ${response.message}")
                }
            } else {
                val isResume = response.code == 206
                val append = isResume && existingBytes > 0L
                val startOffset = if (append) existingBytes else 0L
                writeResponseBody(response, partFile, append, startOffset, artifact, name)
            }
        } finally {
            response.close()
        }

        _state.value = InstallState.Verifying(name)
        val finalHash = computeSha256(partFile)
        if (!finalHash.equals(artifact.sha256, ignoreCase = true)) {
            partFile.delete()
            throw IllegalStateException("Контрольная сумма SHA-256 для $name не совпадает. Ожидалось: ${artifact.sha256}, получено: $finalHash")
        }
    }

    private suspend fun writeResponseBody(
        response: Response,
        destination: File,
        append: Boolean,
        startOffset: Long,
        artifact: ArtifactInfo,
        name: String
    ) {
        val body = response.body ?: throw IOException("Пустой ответ от сервера для $name")
        FileOutputStream(destination, append).use { fos ->
            body.byteStream().use { input ->
                val buffer = ByteArray(16384)
                var read: Int
                var total = startOffset
                while (input.read(buffer).also { read = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    fos.write(buffer, 0, read)
                    total += read
                    val progress = if (artifact.sizeBytes > 0) {
                        (total.toFloat() / artifact.sizeBytes).coerceIn(0f, 1f)
                    } else 0f
                    _state.value = InstallState.Downloading(
                        progress = progress,
                        downloadedBytes = total,
                        totalBytes = artifact.sizeBytes,
                        currentArtifact = name
                    )
                }
            }
        }
    }

    private fun configureRootfs(rootfs: File) {
        val etcDir = File(rootfs, "etc").apply { mkdirs() }
        val resolvConf = File(etcDir, "resolv.conf")
        resolvConf.writeText(
            """
            nameserver 8.8.8.8
            nameserver 1.1.1.1
            nameserver 8.8.4.4
            """.trimIndent() + "\n"
        )

        val hosts = File(etcDir, "hosts")
        hosts.writeText(
            """
            127.0.0.1 localhost
            ::1 localhost
            """.trimIndent() + "\n"
        )

        File(rootfs, "tmp").apply {
            mkdirs()
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }
        File(rootfs, "dev").mkdirs()
        File(rootfs, "proc").mkdirs()
        File(rootfs, "sys").mkdirs()
        File(rootfs, "workspace").mkdirs()
        File(rootfs, "root").mkdirs()
    }

    private fun writeMarker(rootfs: File, runtimeVer: String, alpineVer: String) {
        val marker = File(rootfs, ".pocketcli_ready")
        val content = """{"runtimeVersion":"$runtimeVer","alpineVersion":"$alpineVer","installedAt":${System.currentTimeMillis()}}"""
        marker.writeText(content)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun findFileByName(dir: File, name: String): File? {
        val candidates = dir.walkTopDown()
        for (f in candidates) {
            if (f.isFile && f.name == name) {
                return f
            }
        }
        return null
    }

    private fun copyDirectoryRecursively(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { file ->
            val dest = File(target, file.name)
            if (file.isDirectory) {
                copyDirectoryRecursively(file, dest)
            } else {
                file.copyTo(dest, overwrite = true)
            }
        }
    }
}
