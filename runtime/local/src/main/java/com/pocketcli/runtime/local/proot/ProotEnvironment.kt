package com.pocketcli.runtime.local.proot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProotEnvironment(
    private val nativeLibDirProvider: () -> File,
    private val filesDirProvider: () -> File
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        nativeLibDirProvider = { File(context.applicationInfo.nativeLibraryDir) },
        filesDirProvider = { context.filesDir }
    )

    val nativeLibraryDir: File
        get() = nativeLibDirProvider()

    val prootBinary: File
        get() = File(nativeLibraryDir, "libproot.so")

    val prootLoader: File
        get() = File(nativeLibraryDir, "libproot-loader.so")

    val tmpDir: File
        get() = File(filesDirProvider(), "runtime/tmp").apply {
            if (!exists()) mkdirs()
        }

    fun isProotAvailable(): Boolean {
        return prootBinary.exists() && prootLoader.exists()
    }

    fun buildProotCommand(
        rootfsDir: File,
        command: List<String>,
        binds: List<Pair<File, String>> = emptyList(),
        workingDir: String = "/"
    ): List<String> {
        val cmd = mutableListOf<String>()
        cmd.add(prootBinary.absolutePath)
        cmd.add("-0") // Run as fake root
        cmd.add("-r")
        cmd.add(rootfsDir.absolutePath)

        // Standard pseudofilesystem bindings
        cmd.add("-b")
        cmd.add("/dev")
        cmd.add("-b")
        cmd.add("/proc")
        cmd.add("-b")
        cmd.add("/sys")

        // Custom workspace and storage bindings
        for ((hostDir, guestPath) in binds) {
            cmd.add("-b")
            cmd.add("${hostDir.absolutePath}:$guestPath")
        }

        cmd.add("-w")
        cmd.add(workingDir)

        cmd.addAll(command)
        return cmd
    }

    fun getDefaultEnvironment(extraEnv: Map<String, String> = emptyMap()): Map<String, String> {
        val env = mutableMapOf<String, String>()
        env["PROOT_LOADER"] = prootLoader.absolutePath
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["LD_LIBRARY_PATH"] = nativeLibraryDir.absolutePath
        env["HOME"] = "/root"
        env["PATH"] = "/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin:/bin:/sbin"
        env["TERM"] = "xterm-256color"
        env["LANG"] = "C.UTF-8"
        env.putAll(extraEnv)
        return env
    }
}
