package com.pocketcli.core.update

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class UpdateManifestTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `test deserialization of valid update json manifest`() {
        val jsonString = """
        {
          "schemaVersion": 1,
          "channel": "stable",
          "versionName": "1.5.0",
          "versionCode": 105000,
          "tag": "v1.5.0",
          "publishedAt": "2026-10-03T12:00:00Z",
          "minSdk": 28,
          "minSupportedVersionCode": 100000,
          "critical": false,
          "apk": {
            "name": "pocketcli-1.5.0-universal.apk",
            "url": "https://github.com/SH20FK/PocketCLI/releases/download/v1.5.0/pocketcli-1.5.0-universal.apk",
            "size": 12345678,
            "sha256": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
          },
          "notes": [
            "Новая дизайн-система Material 3 Expressive",
            "Автономный рантайм на PRoot"
          ]
        }
        """.trimIndent()

        val manifest = json.decodeFromString<UpdateManifest>(jsonString)

        assertEquals(1, manifest.schemaVersion)
        assertEquals("stable", manifest.channel)
        assertEquals("1.5.0", manifest.versionName)
        assertEquals(105000L, manifest.versionCode)
        assertEquals("v1.5.0", manifest.tag)
        assertEquals(28, manifest.minSdk)
        assertFalse(manifest.critical)
        assertEquals("pocketcli-1.5.0-universal.apk", manifest.apk.name)
        assertEquals(12345678L, manifest.apk.size)
        assertEquals(2, manifest.notes.size)
        assertEquals("Новая дизайн-система Material 3 Expressive", manifest.notes[0])
    }

    @Test
    fun `test serialization roundtrip`() {
        val original = UpdateManifest(
            schemaVersion = 1,
            channel = "beta",
            versionName = "1.6.0-beta.1",
            versionCode = 106001L,
            tag = "v1.6.0-beta.1",
            apk = ApkAsset(
                name = "pocketcli-beta.apk",
                url = "https://example.com/apk",
                size = 50000L,
                sha256 = "11223344"
            ),
            notes = listOf("Beta test")
        )

        val encoded = json.encodeToString(UpdateManifest.serializer(), original)
        val decoded = json.decodeFromString<UpdateManifest>(encoded)

        assertEquals(original.versionName, decoded.versionName)
        assertEquals(original.versionCode, decoded.versionCode)
        assertEquals(original.channel, decoded.channel)
        assertEquals(original.apk.sha256, decoded.apk.sha256)
    }
}
