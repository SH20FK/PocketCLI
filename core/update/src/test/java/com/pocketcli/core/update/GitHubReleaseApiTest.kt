package com.pocketcli.core.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GitHubReleaseApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var releaseApi: GitHubReleaseApi

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClient = OkHttpClient.Builder().build()
        releaseApi = GitHubReleaseApi(okHttpClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun baseUrl(): String = mockWebServer.url("").toString().removeSuffix("/")

    @Test
    fun `test stable channel ignores drafts and prereleases`() = runBlocking {
        val releasesJson = """
            [
                {
                    "tag_name": "v1.0.3-draft",
                    "draft": true,
                    "prerelease": false,
                    "assets": []
                },
                {
                    "tag_name": "v1.0.2-beta.1",
                    "draft": false,
                    "prerelease": true,
                    "assets": [
                        {
                            "name": "update.json",
                            "browser_download_url": "${baseUrl()}/beta/update.json"
                        }
                    ]
                },
                {
                    "tag_name": "v1.0.1",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                        {
                            "name": "update.json",
                            "browser_download_url": "${baseUrl()}/stable/update.json"
                        }
                    ]
                }
            ]
        """.trimIndent()

        val stableManifestJson = """
            {
                "schemaVersion": 1,
                "channel": "stable",
                "versionName": "1.0.1",
                "versionCode": 101,
                "tag": "v1.0.1",
                "apk": {
                    "name": "pocketcli-1.0.1.apk",
                    "url": "https://example.com/apk",
                    "size": 12345,
                    "sha256": "abcdef"
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(stableManifestJson))

        val result = releaseApi.fetchLatestManifest(
            channel = UpdateChannel.STABLE,
            apiBaseUrl = baseUrl()
        )

        assertTrue(result is ManifestFetchResult.Found)
        val found = result as ManifestFetchResult.Found
        assertEquals("v1.0.1", found.manifest.tag)
        assertEquals(101L, found.manifest.versionCode)
    }

    @Test
    fun `test beta channel picks latest prerelease`() = runBlocking {
        val releasesJson = """
            [
                {
                    "tag_name": "v1.0.2-beta.1",
                    "draft": false,
                    "prerelease": true,
                    "assets": [
                        {
                            "name": "update.json",
                            "browser_download_url": "${baseUrl()}/beta/update.json"
                        }
                    ]
                },
                {
                    "tag_name": "v1.0.1",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                        {
                            "name": "update.json",
                            "browser_download_url": "${baseUrl()}/stable/update.json"
                        }
                    ]
                }
            ]
        """.trimIndent()

        val betaManifestJson = """
            {
                "schemaVersion": 1,
                "channel": "beta",
                "versionName": "1.0.2-beta.1",
                "versionCode": 102,
                "tag": "v1.0.2-beta.1",
                "apk": {
                    "name": "pocketcli-1.0.2-beta.1.apk",
                    "url": "https://example.com/apk",
                    "size": 23456,
                    "sha256": "fedcba"
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(betaManifestJson))

        val result = releaseApi.fetchLatestManifest(
            channel = UpdateChannel.BETA,
            apiBaseUrl = baseUrl()
        )

        assertTrue(result is ManifestFetchResult.Found)
        val found = result as ManifestFetchResult.Found
        assertEquals("v1.0.2-beta.1", found.manifest.tag)
        assertEquals(102L, found.manifest.versionCode)
    }

    @Test
    fun `test no matching release returns NoRelease`() = runBlocking {
        val releasesJson = """
            [
                {
                    "tag_name": "v1.0.2-beta.1",
                    "draft": false,
                    "prerelease": true,
                    "assets": []
                }
            ]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson))

        val result = releaseApi.fetchLatestManifest(
            channel = UpdateChannel.STABLE,
            apiBaseUrl = baseUrl()
        )

        assertTrue(result is ManifestFetchResult.NoRelease)
        assertEquals(UpdateChannel.STABLE, (result as ManifestFetchResult.NoRelease).channel)
    }

    @Test
    fun `test release missing update json returns MissingAsset`() = runBlocking {
        val releasesJson = """
            [
                {
                    "tag_name": "v1.0.0",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                        {
                            "name": "app-release.apk",
                            "browser_download_url": "https://example.com/app.apk"
                        }
                    ]
                }
            ]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson))

        val result = releaseApi.fetchLatestManifest(
            channel = UpdateChannel.STABLE,
            apiBaseUrl = baseUrl()
        )

        assertTrue(result is ManifestFetchResult.MissingAsset)
        val missing = result as ManifestFetchResult.MissingAsset
        assertEquals("v1.0.0", missing.tag)
        assertEquals("update.json", missing.asset)
    }

    @Test
    fun `test mismatched tag in manifest returns InvalidManifest`() = runBlocking {
        val releasesJson = """
            [
                {
                    "tag_name": "v1.0.2",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                        {
                            "name": "update.json",
                            "browser_download_url": "${baseUrl()}/update.json"
                        }
                    ]
                }
            ]
        """.trimIndent()

        val manifestJson = """
            {
                "schemaVersion": 1,
                "channel": "stable",
                "versionName": "1.0.0",
                "versionCode": 100,
                "tag": "v1.0.0",
                "apk": {
                    "name": "pocketcli.apk",
                    "url": "https://example.com",
                    "size": 1,
                    "sha256": "abc"
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releasesJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson))

        val result = releaseApi.fetchLatestManifest(
            channel = UpdateChannel.STABLE,
            apiBaseUrl = baseUrl()
        )

        assertTrue(result is ManifestFetchResult.InvalidManifest)
        val invalid = result as ManifestFetchResult.InvalidManifest
        assertEquals("v1.0.2", invalid.tag)
    }
}
