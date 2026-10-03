package com.pocketcli.runtime.local.supervisor

import com.pocketcli.core.model.HealthInfo
import com.pocketcli.core.model.Transport
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.local.db.ConnectionProfileDao
import com.pocketcli.data.local.db.ConnectionProfileEntity
import com.pocketcli.runtime.local.installer.RuntimeInstaller
import com.pocketcli.runtime.local.proot.ProotEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
class LocalRuntimeSupervisor(
    private val prootEnvironment: ProotEnvironment,
    private val runtimeInstaller: RuntimeInstaller,
    private val connectionProfileDao: ConnectionProfileDao,
    private val secretStore: SecretStore,
    val logBuffer: CircularLogBuffer,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val workspacesDirProvider: () -> File,
    private val portAllocator: () -> Int = { allocateFreePort() },
    private val tokenGenerator: () -> String = { generateSecureToken() },
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
        connectionProfileDao: ConnectionProfileDao,
        secretStore: SecretStore,
        logBuffer: CircularLogBuffer
    ) : this(
        prootEnvironment = prootEnvironment,
        runtimeInstaller = runtimeInstaller,
        connectionProfileDao = connectionProfileDao,
        secretStore = secretStore,
        logBuffer = logBuffer,
        okHttpClient = OkHttpClient.Builder().build(),
        ioDispatcher = Dispatchers.IO,
        workspacesDirProvider = {
            File(runtimeInstaller.runtimeBaseDir.parentFile, "workspaces").apply {
                if (!exists()) mkdirs()
            }
        }
    )

    companion object {
        const val LOCAL_PROFILE_ID = "local-runtime"
        const val MAX_RESTARTS = 3
        const val HEALTH_TIMEOUT_SECONDS = 15L
        const val HEALTH_POLL_INTERVAL_MS = 500L
    }

    private val _state = MutableStateFlow<LocalRuntimeState>(LocalRuntimeState.Stopped)
    val state: StateFlow<LocalRuntimeState> = _state.asStateFlow()

    private var currentProcess: Process? = null
    private var processMonitorJob: Job? = null
    private var shouldAutoRestart = true
    private var restartAttempts = 0

    override val baseUrl: String
        get() = (state.value as? LocalRuntimeState.Running)?.let { "http://127.0.0.1:${it.port}" } ?: ""

    override val isConnected: Boolean
        get() = state.value is LocalRuntimeState.Running

    override suspend fun checkHealth(): Result<HealthInfo> = withContext(ioDispatcher) {
        val runningState = state.value as? LocalRuntimeState.Running
            ?: return@withContext Result.failure(IllegalStateException("Локальный рантайм не запущен"))

        queryHealth(runningState.port, runningState.token)
    }

    suspend fun startServer(autoRestart: Boolean = true): Result<Unit> = withContext(ioDispatcher) {
        if (_state.value is LocalRuntimeState.Running || _state.value is LocalRuntimeState.Starting) {
            return@withContext Result.success(Unit)
        }

        shouldAutoRestart = autoRestart
        restartAttempts = 0
        launchStart()
    }

    suspend fun restartServer(): Result<Unit> = withContext(ioDispatcher) {
        stopServer()
        startServer(autoRestart = true)
    }

    suspend fun stopServer(): Result<Unit> = withContext(ioDispatcher) {
        shouldAutoRestart = false
        processMonitorJob?.cancel()
        processMonitorJob = null

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
            val command = listOf(
                "/usr/bin/opencode",
                "serve",
                "--hostname",
                "127.0.0.1",
                "--port",
                port.toString()
            )

            val prootCmd = prootEnvironment.buildProotCommand(
                rootfsDir = runtimeInstaller.rootfsDir,
                command = command,
                binds = listOf(Pair(workspacesDir, guestWorkspace)),
                workingDir = guestWorkspace
            )

            val env = mutableMapOf<String, String>()
            env.putAll(prootEnvironment.getDefaultEnvironment())
            env["OPENCODE_SERVER_PASSWORD"] = token
            env["PORT"] = port.toString()

            // Inject API keys from SecretStore
            val providerKeyNames = listOf(
                "OPENROUTER_API_KEY",
                "ANTHROPIC_API_KEY",
                "OPENAI_API_KEY",
                "DEEPSEEK_API_KEY",
                "GEMINI_API_KEY"
            )
            for (key in providerKeyNames) {
                val secret = secretStore.getSecret(key)
                if (!secret.isNullOrBlank()) {
                    env[key] = secret
                }
            }

            logBuffer.append("[Supervisor] Команда: ${prootCmd.joinToString(" ")}")
            logBuffer.append("[Supervisor] Порт: $port")

            val process = processLauncher(prootCmd, env)
            currentProcess = process

            // Capture streams in background
            scope.launch(Dispatchers.IO) {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { logBuffer.append("[STDOUT] $it") }
                    }
                }
            }

            scope.launch(Dispatchers.IO) {
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
                url = "http://127.0.0.1:$port",
                username = "admin",
                encryptedPassword = secretStore.encrypt(token),
                allowCleartextHttp = true,
                lastConnectedAt = System.currentTimeMillis()
            )
            connectionProfileDao.upsert(profile)

            val pid = runCatching { process.pid() }.getOrNull()
            _state.value = LocalRuntimeState.Running(
                port = port,
                token = token,
                pid = pid,
                startedAt = System.currentTimeMillis()
            )
            logBuffer.append("[Supervisor] Сервер успешно запущен и готов к работе на 127.0.0.1:$port (PID: $pid)")

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
        processMonitorJob = scope.launch(Dispatchers.IO) {
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
        val url = "http://127.0.0.1:$port/global/health"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic("admin", token))
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val healthy = json.optBoolean("healthy", false)
                    val version = json.optString("version", "unknown")
                    Result.success(HealthInfo(healthy = healthy, version = version))
                } else {
                    Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
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
