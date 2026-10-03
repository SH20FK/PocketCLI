package com.pocketcli.core.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ApkVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val verifier = ApkVerifier()

    @Test
    fun `test verifyApk with matching SHA-256 and size`() = runBlocking {
        val file = tempFolder.newFile("test.apk")
        val content = "Sample APK binary content for checksum verification".toByteArray()
        file.writeBytes(content)

        val digest = MessageDigest.getInstance("SHA-256")
        val expectedSha = digest.digest(content).joinToString("") { "%02x".format(it) }

        val result = verifier.verifyApk(file, expectedSha, content.size.toLong())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `test verifyApk fails when SHA-256 does not match`() = runBlocking {
        val file = tempFolder.newFile("corrupted.apk")
        file.writeText("Corrupted content")

        val result = verifier.verifyApk(file, "0000000000000000000000000000000000000000000000000000000000000000")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `test verifyApk fails when size does not match`() = runBlocking {
        val file = tempFolder.newFile("wrong_size.apk")
        file.writeText("Short content")

        val result = verifier.verifyApk(file, "dummy", expectedSize = 99999L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `test verifyApk fails when file does not exist`() = runBlocking {
        val nonExistent = File(tempFolder.root, "does_not_exist.apk")
        val result = verifier.verifyApk(nonExistent, "dummy")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `test verifyApk succeeds with uppercase SHA-256 string`() = runBlocking {
        val file = tempFolder.newFile("uppercase.apk")
        val content = "Uppercase SHA content".toByteArray()
        file.writeBytes(content)

        val digest = MessageDigest.getInstance("SHA-256")
        val expectedSha = digest.digest(content).joinToString("") { "%02X".format(it) }

        val result = verifier.verifyApk(file, expectedSha, content.size.toLong())
        assertTrue(result.isSuccess)
    }
}
