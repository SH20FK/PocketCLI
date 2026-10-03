package com.pocketcli.runtime.local

import com.pocketcli.runtime.local.installer.TarExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class TarExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var targetDir: File

    @Before
    fun setUp() {
        targetDir = tempFolder.newFolder("extract_target")
    }

    @Test
    fun testExtractRegularFilesAndDirectories() {
        val tarGzData = createTarGzArchive(
            listOf(
                TarEntry(name = "etc/", size = 0, type = '5'),
                TarEntry(name = "etc/hello.txt", content = "Hello, PocketCLI!".toByteArray(), mode = 0b110_100_100),
                TarEntry(name = "bin/", size = 0, type = '5'),
                TarEntry(name = "bin/run.sh", content = "#!/bin/sh\necho ok".toByteArray(), mode = 0b111_101_101)
            )
        )

        var fileCount = 0
        var totalBytes = 0L

        TarExtractor.extractTarGz(ByteArrayInputStream(tarGzData), targetDir) { count, bytes ->
            fileCount = count
            totalBytes = bytes
        }

        val helloFile = File(targetDir, "etc/hello.txt")
        assertTrue("etc/hello.txt should exist", helloFile.exists())
        assertEquals("Hello, PocketCLI!", helloFile.readText())

        val runFile = File(targetDir, "bin/run.sh")
        assertTrue("bin/run.sh should exist", runFile.exists())
        assertEquals("#!/bin/sh\necho ok", runFile.readText())
        assertEquals(2, fileCount)
        assertTrue(totalBytes > 0)
    }

    @Test
    fun testZipSlipPathTraversalProtection() {
        val evilTarGz = createTarGzArchive(
            listOf(
                TarEntry(name = "../../evil.txt", content = "evil payload".toByteArray())
            )
        )

        try {
            TarExtractor.extractTarGz(ByteArrayInputStream(evilTarGz), targetDir)
            fail("Should throw SecurityException on Zip-Slip path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Path traversal") == true)
        }
    }

    private data class TarEntry(
        val name: String,
        val content: ByteArray = ByteArray(0),
        val size: Long = content.size.toLong(),
        val mode: Int = 0b110_100_100,
        val type: Char = '0'
    )

    private fun createTarGzArchive(entries: List<TarEntry>): ByteArray {
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

        // Two empty 512-byte blocks at end of tar
        gzos.write(ByteArray(1024))
        gzos.finish()
        return bos.toByteArray()
    }
}
