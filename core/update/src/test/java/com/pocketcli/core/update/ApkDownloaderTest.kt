package com.pocketcli.core.update

import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class ApkDownloaderTest {

    private val downloader = ApkDownloader(OkHttpClient())

    @Test
    fun `test validateDownloadUrl accepts github domains with HTTPS`() {
        assertTrue(downloader.validateDownloadUrl("https://github.com/SH20FK/PocketCLI/releases/download/v1.5.0/app.apk"))
        assertTrue(downloader.validateDownloadUrl("https://raw.githubusercontent.com/SH20FK/PocketCLI/main/app.apk"))
        assertTrue(downloader.validateDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/12345/app.apk"))
    }

    @Test
    fun `test validateDownloadUrl rejects plain HTTP or untrusted hosts`() {
        assertFalse(downloader.validateDownloadUrl("http://github.com/SH20FK/PocketCLI/app.apk"))
        assertFalse(downloader.validateDownloadUrl("https://evil.com/app.apk"))
        assertFalse(downloader.validateDownloadUrl("https://notgithub.com/app.apk"))
        assertFalse(downloader.validateDownloadUrl("ftp://github.com/app.apk"))
        assertFalse(downloader.validateDownloadUrl(""))
        assertFalse(downloader.validateDownloadUrl("invalid-url"))
    }
}
