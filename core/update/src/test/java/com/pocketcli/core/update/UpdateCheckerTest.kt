package com.pocketcli.core.update

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {

    private val checker = UpdateChecker()

    @Test
    fun `test isUpdateAvailable returns true when manifest code is greater`() {
        val manifest = UpdateManifest(
            versionName = "1.5.0",
            versionCode = 1005000L,
            tag = "v1.5.0",
            apk = ApkAsset(name = "test.apk", url = "https://github.com/SH20FK/PocketCLI/test.apk", size = 1000L, sha256 = "abc")
        )

        assertTrue(checker.isUpdateAvailable(1000000L, manifest))
        assertFalse(checker.isUpdateAvailable(1005000L, manifest))
        assertFalse(checker.isUpdateAvailable(1006000L, manifest))
    }

    @Test
    fun `test isCompatible checks minSupportedVersionCode`() {
        val manifest = UpdateManifest(
            versionName = "2.0.0",
            versionCode = 2000000L,
            tag = "v2.0.0",
            minSupportedVersionCode = 1005000L,
            apk = ApkAsset(name = "test.apk", url = "https://github.com/SH20FK/PocketCLI/test.apk", size = 1000L, sha256 = "abc")
        )

        assertTrue(checker.isCompatible(1005000L, manifest))
        assertTrue(checker.isCompatible(1006000L, manifest))
        assertFalse(checker.isCompatible(1004000L, manifest))
    }

    @Test
    fun `test SemVer formula correctly matches specification`() {
        val computedCode = checker.computeVersionCode(major = 1, minor = 5, patch = 0)
        assertEquals(1005000L, computedCode)

        val computedCode2 = checker.computeVersionCode(major = 0, minor = 3, patch = 2)
        assertEquals(3002L, computedCode2)
    }

    @Test
    fun `test shouldNotify handles skipped versions unless critical`() {
        val manifest = UpdateManifest(
            versionName = "1.5.0",
            versionCode = 1005000L,
            tag = "v1.5.0",
            critical = false,
            apk = ApkAsset(name = "test.apk", url = "https://github.com/SH20FK/PocketCLI/test.apk", size = 1000L, sha256 = "abc")
        )

        assertFalse(checker.shouldNotify(manifest, skippedTag = "v1.5.0"))
        assertTrue(checker.shouldNotify(manifest, skippedTag = "v1.4.0"))

        val criticalManifest = manifest.copy(critical = true)
        assertTrue(checker.shouldNotify(criticalManifest, skippedTag = "v1.5.0"))
    }

    @Test
    fun `test isUpdateAvailable with different versionName when versionCode matches`() {
        val manifest = UpdateManifest(
            versionName = "1.0.9-beta.5",
            versionCode = 1000009L,
            tag = "v1.0.9-beta.5",
            apk = ApkAsset(name = "test.apk", url = "https://github.com/SH20FK/PocketCLI/test.apk", size = 1000L, sha256 = "abc")
        )

        // Same versionCode, but different versionName -> should return true
        assertTrue(checker.isUpdateAvailable(1000009L, manifest, currentVersionName = "1.0.9-beta.4"))
        // Same versionCode and same versionName -> false
        assertFalse(checker.isUpdateAvailable(1000009L, manifest, currentVersionName = "1.0.9-beta.5"))
    }
}
