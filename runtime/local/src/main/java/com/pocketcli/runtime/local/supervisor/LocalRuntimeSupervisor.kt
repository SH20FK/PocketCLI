package com.pocketcli.runtime.local.supervisor

import com.pocketcli.core.model.HealthInfo
import com.pocketcli.core.model.Transport
import com.pocketcli.core.security.ProviderKeyStore
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.local.db.ConnectionProfileEntity
import com.pocketcli.data.local.db.ProfileDao
import com.pocketcli.runtime.local.installer.RuntimeInstaller
import com.pocketcli.runtime.local.proot.ProotEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LocalRuntimeSupervisor(
    private val prootEnvironment: ProotEnvironment,
    private val runtimeInstaller: RuntimeInstaller,
    private val profileDao: ProfileDao,
    private val secretStore: SecretStore,
    open val logBuffer: CircularLogBuffer,
    private val host: String = "127.0.0.1",
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val workspacesDirProvider: () -> File,
    private val portAllocator: () -> Int = { allocateFreePort() },
    private val tokenGenerator: () -> String = { generateSecureToken() },
    private val providerKeyProvider: () -> Map<String, String> = { emptyMap() },
    private val processLauncher: (cmd: List<String>, env: Map<String, String>) -> Process = { cmd, env ->
        ProcessBuilder(cmd).apply {
            environment().putAll(env)
        }.start()
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) : Transport {

    @Inject
    constructor(
        prootEnvironment: ProotEnvironment,
        runtimeInstaller: RuntimeInstaller,
        profileDao: ProfileDao,
        secretStore: SecretStore,
        logBuffer: CircularLogBuffer,
        providerKeyStore: ProviderKeyStore
    ) : this(
        prootEnvironment = prootEnvironment,
        runtimeInstaller = runtimeInstaller,
        profileDao = profileDao,
        secretStore = secretStore,
        logBuffer = logBuffer,
        host = "127.0.0.1",
        okHttpClient = OkHttpClient.Builder().build(),
        ioDispatcher = Dispatchers.IO,
        workspacesDirProvider = {
            File(runtimeInstaller.runtimeBaseDir.parentFile, "workspaces").apply {
                if (!exists()) mkdirs()
            }
        },
        providerKeyProvider = { kotlinx.coroutines.runBlocking { providerKeyStore.getAllDecrypted() } }
    )

    companion object {
        const val LOCAL_PROFILE_ID = "local-runtime"
        const val MAX_RESTARTS = 3
        const val HEALTH_TIMEOUT_SECONDS = 15L
        const val HEALTH_POLL_INTERVAL_MS = 500L
    }

    private val _state = MutableStateFlow<LocalRuntimeState>(LocalRuntimeState.Stopped)
    open val state: StateFlow<LocalRuntimeState> = _state.asStateFlow()

    private var currentProcess: Process? = null
    private var processMonitorJob: Job? = null
    private var shouldAutoRestart = true
    private var restartAttempts = 0

    override val baseUrl: String
        get() = (state.value as? LocalRuntimeState.Running)?.let { "http://$host:${it.port}" } ?: ""

    override val isConnected: Boolean
        get() = state.value is LocalRuntimeState.Running

    override suspend fun checkHealth(): Result<HealthInfo> = withContext(ioDispatcher) {
        val runningState = state.value as? LocalRuntimeState.Running
            ?: return@withContext Result.failure(IllegalStateException("Локальный рантайм не запущен"))

        queryHealth(runningState.port, runningState.token)
    }

    open suspend fun startServer(autoRestart: Boolean = true): Result<Unit> = withContext(ioDispatcher) {
        if (_state.value is LocalRuntimeState.Running || _state.value is LocalRuntimeState.Starting) {
            return@withContext Result.success(Unit)
        }

        shouldAutoRestart = autoRestart
        restartAttempts = 0
        launchStart()
    }

    open suspend fun restartServer(): Result<Unit> = withContext(ioDispatcher) {
        stopServer()
        startServer(autoRestart = true)
    }

    open suspend fun stopServer(): Result<Unit> = withContext(ioDispatcher) {
        shouldAutoRestart = false
        val job = processMonitorJob
        processMonitorJob = null
        job?.cancel()

        if (_state.value is LocalRuntimeState.Stopped) {
            return@withContext Result.success(Unit)
        }

        _state.value = LocalRuntimeState.Stopping
        logBuffer.append("[Supervisor] Остановка сервера...")

        val proc = currentProcess
        currentProcess = null

        if (proc != null && proc.isAlive) {
            proc.destroy()
            try {
                withTimeout(2000) {
                    while (proc.isAlive) {
                        delay(100)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                proc.destroyForcibly()
            }
        }

        job?.join()

        _state.value = LocalRuntimeState.Stopped
        logBuffer.append("[Supervisor] Сервер остановлен.")
        Result.success(Unit)
    }

    private suspend fun launchStart(): Result<Unit> {
        if (!runtimeInstaller.isInstalled()) {
            val err = "Рантайм не установлен. Сначала выполните установку."
            _state.value = LocalRuntimeState.Failed(err, canRestart = false)
            logBuffer.append("[Supervisor] Ошибка: $err")
            return Result.failure(IllegalStateException(err))
        }

        if (!prootEnvironment.isProotAvailable()) {
            val err = "Бинарники PRoot недоступны."
            _state.value = LocalRuntimeState.Failed(err, canRestart = false)
            logBuffer.append("[Supervisor] Ошибка: $err")
            return Result.failure(IllegalStateException(err))
        }

        _state.value = LocalRuntimeState.Starting
        logBuffer.append("[Supervisor] Инициализация запуска локального сервера OpenCode...")

        try {
            val port = portAllocator()
            val token = tokenGenerator()

            val workspacesDir = workspacesDirProvider().apply {
                if (!exists()) mkdirs()
            }

            val guestWorkspace = "/workspace"
            val providerKeys = providerKeyProvider()

            val guestCommand = mutableListOf(
                "/usr/bin/env",
                "-i",
                "HOME=/root",
                "PATH=/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin:/bin:/sbin",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "OPENCODE_SERVER_PASSWORD=$token",
                "PORT=$port"
            )
            for ((k, v) in providerKeys) {
                guestCommand.add("$k=$v")
            }
            guestCommand.addAll(
                listOf(
                    "/usr/bin/opencode",
                    "serve",
                    "--hostname",
                    host,
                    "--port",
                    port.toString()
                )
            )

            val prootCmd = prootEnvironment.buildProotCommand(
                rootfsDir = runtimeInstaller.rootfsDir,
                command = guestCommand,
                binds = listOf(Pair(workspacesDir, guestWorkspace)),
                workingDir = guestWorkspace
            )

            val env = mutableMapOf<String, String>()
            env.putAll(prootEnvironment.getDefaultEnvironment())
            env["OPENCODE_SERVER_PASSWORD"] = token
            env["PORT"] = port.toString()
            env.putAll(providerKeys)

            logBuffer.append("[Supervisor] Команда: ${prootCmd.joinToString(" ")}")
            logBuffer.append("[Supervisor] Порт: $port")

            val process = processLauncher(prootCmd, env)
            currentProcess = process

            // Capture streams in background
            scope.launch(ioDispatcher) {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { logBuffer.append("[STDOUT] $it") }
                    }
                }
            }

            scope.launch(ioDispatcher) {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { logBuffer.append("[STDERR] $it") }
                    }
                }
            }

            // Health check loop
            logBuffer.append("[Supervisor] Ожидание готовности сервера...")
            val ready = pollHealthWithTimeout(port, token, HEALTH_TIMEOUT_SECONDS)

            if (!ready) {
                val exitCode = if (!process.isAlive) process.exitValue() else null
                process.destroyForcibly()
                currentProcess = null
                val err = "Сервер не ответил на health-check в течение ${HEALTH_TIMEOUT_SECONDS}с (код выхода: $exitCode)"
                _state.value = LocalRuntimeState.Failed(err, exitCode = exitCode, canRestart = true)
                logBuffer.append("[Supervisor] Ошибка: $err")
                return Result.failure(IllegalStateException(err))
            }

            // Register or update profile in DB
            val profile = ConnectionProfileEntity(
                id = LOCAL_PROFILE_ID,
                name = "Локальный агент",
                url = "http://$host:$port",
                username = "admin",
                encryptedPassword = secretStore.encrypt(token),
                allowCleartextHttp = true,
                lastConnectedAt = System.currentTimeMillis()
            )
            profileDao.upsert(profile)

            val pid = getProcessPid(process)
            _state.value = LocalRuntimeState.Running(
                port = port,
                token = token,
                pid = pid,
                startedAt = System.currentTimeMillis()
            )
            logBuffer.append("[Supervisor] Сервер успешно запущен и готов к работе на $host:$port (PID: $pid)")

            // Start monitoring process exit for auto-restart
            startProcessMonitor(process)

            return Result.success(Unit)
        } catch (e: Exception) {
            val err = "Исключение при запуске сервера: ${e.message}"
            _state.value = LocalRuntimeState.Failed(err, canRestart = true)
            logBuffer.append("[Supervisor] $err")
            return Result.failure(e)
        }
    }

    private fun startProcessMonitor(process: Process) {
        processMonitorJob?.cancel()
        processMonitorJob = scope.launch(ioDispatcher) {
            try {
                val exitCode = process.waitFor()
                if (isActive && _state.value is LocalRuntimeState.Running) {
                    logBuffer.append("[Supervisor] Процесс неожиданно завершился с кодом $exitCode")
                    if (shouldAutoRestart && restartAttempts < MAX_RESTARTS) {
                        restartAttempts++
                        val backoffMs = (1L shl (restartAttempts - 1)) * 1000L
                        logBuffer.append("[Supervisor] Перезапуск ($restartAttempts/$MAX_RESTARTS) через ${backoffMs}мс...")
                        delay(backoffMs)
                        launchStart()
                    } else {
                        val reason = "Процесс аварийно завершился с кодом $exitCode"
                        _state.value = LocalRuntimeState.Failed(reason, exitCode = exitCode, canRestart = true)
                        logBuffer.append("[Supervisor] Превышен лимит попыток перезапуска. $reason")
                    }
                }
            } catch (_: InterruptedException) {
                // Normal cancellation
            } catch (_: CancellationException) {
                // Normal coroutine cancellation
            }
        }
    }

    private suspend fun pollHealthWithTimeout(port: Int, token: String, timeoutSeconds: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            val proc = currentProcess
            if (proc != null && !proc.isAlive) {
                return false
            }

            val health = queryHealth(port, token)
            if (health.isSuccess && health.getOrNull()?.healthy == true) {
                return true
            }
            delay(HEALTH_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun queryHealth(port: Int, token: String): Result<HealthInfo> {
        val url = "http://$host:$port/global/health"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic("admin", token))
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val parsed = healthJson.decodeFromString<HealthCheckResponse>(body)
                    Result.success(HealthInfo(healthy = parsed.healthy, version = parsed.version))
                } else {
                    Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            logBuffer.append("[Supervisor] Health-check call error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun getProcessPid(process: Process): Long? {
        return try {
            val pidMethod = process.javaClass.getMethod("pid")
            (pidMethod.invoke(process) as? Number)?.toLong()
        } catch (_: Exception) {
            try {
                val field = process.javaClass.getDeclaredField("id")
                field.isAccessible = true
                (field.get(process) as? Number)?.toLong()
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun allocateFreePort(): Int {
    return ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
}

private fun generateSecureToken(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

@Serializable
private data class HealthCheckResponse(
    val healthy: Boolean = false,
    val version: String = "unknown"
)

private val healthJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
