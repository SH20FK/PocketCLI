package com.pocketcli.runtime.local

import com.pocketcli.runtime.local.installer.InstallState
import com.pocketcli.runtime.local.installer.RuntimeInstaller
import com.pocketcli.runtime.local.manifest.ManifestParser
import com.pocketcli.runtime.local.proot.ProotEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var nativeLibDir: File
    private lateinit var prootEnv: ProotEnvironment
    private lateinit var manifestParser: ManifestParser
    private lateinit var mockWebServer: MockWebServer
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("filesDir")
        nativeLibDir = tempFolder.newFolder("nativeLibDir")

        // Create dummy proot binaries
        File(nativeLibDir, "libproot.so").writeText("dummy-proot")
        File(nativeLibDir, "libproot-loader.so").writeText("dummy-loader")

        prootEnv = ProotEnvironment(
            nativeLibDirProvider = { nativeLibDir },
            filesDirProvider = { filesDir }
        )
        manifestParser = ManifestParser()
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testResolveArch() {
        val installer = createInstaller(manifestJson = "{}")
        assertEquals("aarch64", installer.resolveArch(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("x86_64", installer.resolveArch(arrayOf("x86_64", "x86")))
        assertEquals(null, installer.resolveArch(arrayOf("armeabi-v7a", "x86")))
    }

    @Test
    fun testPrerequisiteMissingProotFails() = runTest(testDispatcher) {
        File(nativeLibDir, "libproot.so").delete()

        val installer = createInstaller(manifestJson = "{}")
        val result = installer.install()

        assertTrue(result.isFailure)
        assertTrue(installer.state.value is InstallState.Failed)
        assertFalse(installer.isInstalled())
    }

    @Test
    fun testPrerequisiteUnsupportedArchFails() = runTest(testDispatcher) {
        val installer = createInstaller(
            manifestJson = "{}",
            supportedAbis = arrayOf("armeabi-v7a")
        )
        val result = installer.install()

        assertTrue(result.isFailure)
        assertTrue(installer.state.value is InstallState.Failed)
    }

    @Test
    fun testPrerequisiteInsufficientSpaceFails() = runTest(testDispatcher) {
        val validManifest = """
        {
            "manifestVersion": 1,
            "runtimeVersion": "1.2.27",
            "alpineVersion": "3.21.3",
            "artifacts": {
                "x86_64": {
                    "rootfs": {"url":"http://localhost/r.tar.gz","sha256":"0000000000000000000000000000000000000000000000000000000000000000","sizeBytes":100},
                    "opencode": {"url":"http://localhost/o.tgz","sha256":"0000000000000000000000000000000000000000000000000000000000000000","sizeBytes":100}
                },
                "aarch64": {
                    "rootfs": {"url":"http://localhost/r.tar.gz","sha256":"0000000000000000000000000000000000000000000000000000000000000000","sizeBytes":100},
                    "opencode": {"url":"http://localhost/o.tgz","sha256":"0000000000000000000000000000000000000000000000000000000000000000","sizeBytes":100}
                }
            }
        }
        """.trimIndent()

        val installer = createInstaller(
            manifestJson = validManifest,
            freeSpace = 50L * 1024L * 1024L // 50MB < 350MB
        )
        val result = installer.install()

        assertTrue(result.isFailure)
        assertTrue(installer.state.value is InstallState.Failed)
        val errorMsg = (installer.state.value as InstallState.Failed).error
        assertTrue(errorMsg.contains("Недостаточно свободного места"))
    }

    @Test
    fun testSuccessfulInstallAndConfiguration() = runTest(testDispatcher) {
        val rootfsTarGz = createTarGzArchive(
            listOf(
                TarTestEntry(name = "bin/", type = '5'),
                TarTestEntry(name = "bin/sh", content = "#!/bin/sh\n".toByteArray())
            )
        )
        val opencodeTarGz = createTarGzArchive(
            listOf(
                TarTestEntry(name = "package/bin/opencode", content = "#!/bin/sh\necho opencode 1.2.27".toByteArray(), mode = 0b111_101_101)
            )
        )

        val rootfsSha = computeSha256(rootfsTarGz)
        val opencodeSha = computeSha256(opencodeTarGz)

        val manifestJson = """
        {
            "manifestVersion": 1,
            "runtimeVersion": "1.2.27",
            "alpineVersion": "3.21.3",
            "artifacts": {
                "x86_64": {
                    "rootfs": {
                        "url": "${mockWebServer.url("/rootfs.tar.gz")}",
                        "sha256": "$rootfsSha",
                        "sizeBytes": ${rootfsTarGz.size}
                    },
                    "opencode": {
                        "url": "${mockWebServer.url("/opencode.tgz")}",
                        "sha256": "$opencodeSha",
                        "sizeBytes": ${opencodeTarGz.size}
                    }
                },
                "aarch64": {
                    "rootfs": {
                        "url": "${mockWebServer.url("/rootfs.tar.gz")}",
                        "sha256": "$rootfsSha",
                        "sizeBytes": ${rootfsTarGz.size}
                    },
                    "opencode": {
                        "url": "${mockWebServer.url("/opencode.tgz")}",
                        "sha256": "$opencodeSha",
                        "sizeBytes": ${opencodeTarGz.size}
                    }
                }
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(rootfsTarGz)))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(opencodeTarGz)))

        val installer = createInstaller(
            manifestJson = manifestJson,
            supportedAbis = arrayOf("x86_64")
        )

        val result = installer.install()
        assertTrue("Install should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertTrue(installer.isInstalled())
        assertEquals("1.2.27", installer.getInstalledVersion())
        assertTrue(installer.state.value is InstallState.Ready)

        // Verify rootfs structure
        val rootfsDir = File(filesDir, "runtime/rootfs")
        assertTrue(File(rootfsDir, "etc/resolv.conf").exists())
        assertTrue(File(rootfsDir, "etc/hosts").exists())
        assertTrue(File(rootfsDir, "usr/bin/opencode").exists())
        assertTrue(File(rootfsDir, "usr/local/bin/opencode").exists())
        assertTrue(File(rootfsDir, ".pocketcli_ready").exists())

        // Verify resolv.conf contains DNS
        val resolvText = File(rootfsDir, "etc/resolv.conf").readText()
        assertTrue(resolvText.contains("nameserver 8.8.8.8"))

        // Test uninstall
        val uninstallResult = installer.uninstall()
        assertTrue(uninstallResult.isSuccess)
        assertFalse(installer.isInstalled())
        assertFalse(rootfsDir.exists())
    }

    @Test
    fun testSha256MismatchFailsInstallation() = runTest(testDispatcher) {
        val rootfsTarGz = createTarGzArchive(
            listOf(TarTestEntry(name = "bin/sh", content = "sh".toByteArray()))
        )

        val manifestJson = """
        {
            "manifestVersion": 1,
            "runtimeVersion": "1.2.27",
            "alpineVersion": "3.21.3",
            "artifacts": {
                "x86_64": {
                    "rootfs": {
                        "url": "${mockWebServer.url("/rootfs.tar.gz")}",
                        "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
                        "sizeBytes": ${rootfsTarGz.size}
                    },
                    "opencode": {
                        "url": "${mockWebServer.url("/opencode.tgz")}",
                        "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
                        "sizeBytes": 100
                    }
                },
                "aarch64": {
                    "rootfs": {
                        "url": "${mockWebServer.url("/rootfs.tar.gz")}",
                        "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
                        "sizeBytes": ${rootfsTarGz.size}
                    },
                    "opencode": {
                        "url": "${mockWebServer.url("/opencode.tgz")}",
                        "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
                        "sizeBytes": 100
                    }
                }
            }
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(rootfsTarGz)))

        val installer = createInstaller(
            manifestJson = manifestJson,
            supportedAbis = arrayOf("x86_64")
        )

        val result = installer.install()
        assertTrue(result.isFailure)
        assertTrue(installer.state.value is InstallState.Failed)
        assertFalse(installer.isInstalled())
    }

    private fun createInstaller(
        manifestJson: String,
        supportedAbis: Array<String> = arrayOf("x86_64"),
        freeSpace: Long = 1024L * 1024L * 1024L // 1 GB
    ): RuntimeInstaller {
        return RuntimeInstaller(
            prootEnvironment = prootEnv,
            manifestParser = manifestParser,
            okHttpClient = OkHttpClient.Builder().build(),
            ioDispatcher = testDispatcher,
            filesDirProvider = { filesDir },
            supportedAbisProvider = { supportedAbis },
            freeSpaceProvider = { freeSpace },
            manifestContentProvider = { manifestJson }
        )
    }

    private data class TarTestEntry(
        val name: String,
        val content: ByteArray = ByteArray(0),
        val size: Long = content.size.toLong(),
        val mode: Int = 0b110_100_100,
        val type: Char = '0'
    )

    private fun createTarGzArchive(entries: List<TarTestEntry>): ByteArray {
        val bos = ByteArrayOutputStream()
        val gzos = GZIPOutputStream(bos)

        for (entry in entries) {
            val header = ByteArray(512)
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))

            val modeStr = "%07o".format(entry.mode)
            System.arraycopy(modeStr.toByteArray(), 0, header, 100, modeStr.length)

            val sizeStr = "%011o".format(entry.size)
            System.arraycopy(sizeStr.toByteArray(), 0, header, 124, sizeStr.length)

            header[156] = entry.type.code.toByte()
            System.arraycopy("ustar\u0000".toByteArray(), 0, header, 257, 6)

            for (i in 148 until 156) header[i] = ' '.code.toByte()
            var sum = 0
            for (b in header) {
                sum += (b.toInt() and 0xFF)
            }
            val sumStr = "%06o\u0000 ".format(sum)
            System.arraycopy(sumStr.toByteArray(), 0, header, 148, sumStr.length)

            gzos.write(header)
            if (entry.content.isNotEmpty()) {
                gzos.write(entry.content)
                val padding = (512 - (entry.content.size % 512)) % 512
                if (padding > 0) {
                    gzos.write(ByteArray(padding))
                }
            }
        }

        gzos.write(ByteArray(1024))
        gzos.finish()
        return bos.toByteArray()
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
