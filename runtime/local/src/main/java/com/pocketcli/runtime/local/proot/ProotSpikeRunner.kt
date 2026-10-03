package com.pocketcli.runtime.local.proot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SpikeResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val error: String? = null
)

@Singleton
class ProotSpikeRunner @Inject constructor(
    private val environment: ProotEnvironment
) {

    fun checkEnvironment(): Result<Unit> {
        if (!environment.isProotAvailable()) {
            return Result.failure(
                IllegalStateException("PRoot binaries not found in nativeLibraryDir: ${environment.nativeLibraryDir.absolutePath}")
            )
        }
        return Result.success(Unit)
    }

    suspend fun runCommand(
        rootfsDir: File,
        command: List<String>,
        binds: List<Pair<File, String>> = emptyList(),
        timeoutSeconds: Long = 30
    ): SpikeResult = withContext(Dispatchers.IO) {
        val check = checkEnvironment()
        if (check.isFailure) {
            return@withContext SpikeResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "",
                error = check.exceptionOrNull()?.message
            )
        }

        try {
            val cmd = environment.buildProotCommand(rootfsDir, command, binds)
            val pb = ProcessBuilder(cmd)
            val env = pb.environment()
            env.putAll(environment.getDefaultEnvironment())

            val process = pb.start()

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stdoutBuilder.append(line).append("\n")
                    }
                }
            }

            val stderrThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stderrBuilder.append(line).append("\n")
                    }
                }
            }

            stdoutThread.start()
            stderrThread.start()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@withContext SpikeResult(
                    success = false,
                    exitCode = -1,
                    stdout = stdoutBuilder.toString().trim(),
                    stderr = stderrBuilder.toString().trim(),
                    error = "Process timed out after ${timeoutSeconds}s"
                )
            }

            stdoutThread.join(2000)
            stderrThread.join(2000)

            val exitCode = process.exitValue()
            SpikeResult(
                success = exitCode == 0,
                exitCode = exitCode,
                stdout = stdoutBuilder.toString().trim(),
                stderr = stderrBuilder.toString().trim()
            )
        } catch (e: Exception) {
            SpikeResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "",
                error = e.localizedMessage ?: e.message
            )
        }
    }

    suspend fun testEcho(rootfsDir: File): SpikeResult {
        return runCommand(rootfsDir, listOf("/bin/sh", "-c", "echo 'proot-ok'"))
    }

    suspend fun testOpenCodeVersion(rootfsDir: File): SpikeResult {
        return runCommand(rootfsDir, listOf("/bin/sh", "-c", "opencode --version"))
    }
}
