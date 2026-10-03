package com.pocketcli.runtime.local

import com.pocketcli.runtime.local.proot.ProotEnvironment
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ProotEnvironmentTest {

    private lateinit var tempDir: File
    private lateinit var nativeLibsDir: File
    private lateinit var filesDir: File
    private lateinit var environment: ProotEnvironment

    @Before
    fun setup() {
        tempDir = createTempDir("proot_test")
        nativeLibsDir = File(tempDir, "lib").apply { mkdirs() }
        filesDir = File(tempDir, "files").apply { mkdirs() }

        environment = ProotEnvironment(
            nativeLibDirProvider = { nativeLibsDir },
            filesDirProvider = { filesDir }
        )
    }

    @Test
    fun testIsProotAvailable() {
        assertFalse(environment.isProotAvailable())

        File(nativeLibsDir, "libproot.so").writeText("elf")
        assertFalse(environment.isProotAvailable())

        File(nativeLibsDir, "libproot-loader.so").writeText("elf")
        assertTrue(environment.isProotAvailable())
    }

    @Test
    fun testBuildProotCommand() {
        File(nativeLibsDir, "libproot.so").writeText("elf")
        File(nativeLibsDir, "libproot-loader.so").writeText("elf")

        val rootfsDir = File(tempDir, "rootfs").apply { mkdirs() }
        val workspaceHost = File(tempDir, "workspace").apply { mkdirs() }

        val cmd = environment.buildProotCommand(
            rootfsDir = rootfsDir,
            command = listOf("/bin/sh", "-c", "echo hello"),
            binds = listOf(Pair(workspaceHost, "/workspace")),
            workingDir = "/workspace"
        )

        assertEquals(environment.prootBinary.absolutePath, cmd[0])
        assertEquals("-0", cmd[1])
        assertEquals("-r", cmd[2])
        assertEquals(rootfsDir.absolutePath, cmd[3])

        assertTrue(cmd.contains("-b"))
        assertTrue(cmd.contains("/dev"))
        assertTrue(cmd.contains("/proc"))
        assertTrue(cmd.contains("/sys"))
        assertTrue(cmd.contains("${workspaceHost.absolutePath}:/workspace"))

        val wIndex = cmd.indexOf("-w")
        assertTrue(wIndex != -1)
        assertEquals("/workspace", cmd[wIndex + 1])

        assertTrue(cmd.contains("/bin/sh"))
        assertTrue(cmd.contains("echo hello"))
    }

    @Test
    fun testGetDefaultEnvironment() {
        File(nativeLibsDir, "libproot-loader.so").writeText("loader")

        val env = environment.getDefaultEnvironment(mapOf("FOO" to "BAR"))
        assertEquals(environment.prootLoader.absolutePath, env["PROOT_LOADER"])
        assertEquals(environment.tmpDir.absolutePath, env["PROOT_TMP_DIR"])
        assertEquals("/root", env["HOME"])
        assertEquals("xterm-256color", env["TERM"])
        assertEquals("BAR", env["FOO"])
    }
}
