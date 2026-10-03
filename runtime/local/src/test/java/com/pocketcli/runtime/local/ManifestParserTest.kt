package com.pocketcli.runtime.local

import com.pocketcli.runtime.local.manifest.ManifestParser
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ManifestParserTest {

    private lateinit var parser: ManifestParser

    @Before
    fun setup() {
        parser = ManifestParser()
    }

    @Test
    fun testParseValidManifest() {
        val json = """
            {
              "manifestVersion": 1,
              "runtimeVersion": "1.2.27",
              "alpineVersion": "3.21.3",
              "artifacts": {
                "aarch64": {
                  "rootfs": {
                    "url": "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.3-aarch64.tar.gz",
                    "sha256": "ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea",
                    "sizeBytes": 3850365
                  },
                  "opencode": {
                    "url": "https://registry.npmjs.org/opencode-linux-arm64-musl/-/opencode-linux-arm64-musl-1.2.27.tgz",
                    "sha256": "388b9380d5e203cee9aec071d810a8ab6a717702f94f932ef2446829ec035a92",
                    "sizeBytes": 44518105
                  }
                },
                "x86_64": {
                  "rootfs": {
                    "url": "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/x86_64/alpine-minirootfs-3.21.3-x86_64.tar.gz",
                    "sha256": "1a694899e406ce55d32334c47ac0b2efb6c06d7e878102d1840892ad44cd5239",
                    "sizeBytes": 3507952
                  },
                  "opencode": {
                    "url": "https://registry.npmjs.org/opencode-linux-x64-musl/-/opencode-linux-x64-musl-1.2.27.tgz",
                    "sha256": "57c28b1787e30590c299d34d90aee9dbb6d8ad59aa83702643fb43c9f2bc9157",
                    "sizeBytes": 44397267
                  }
                }
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(1, manifest.manifestVersion)
        assertEquals("1.2.27", manifest.runtimeVersion)
        assertEquals("3.21.3", manifest.alpineVersion)

        val arm64 = manifest.artifacts["aarch64"]
        assertNotNull(arm64)
        assertEquals(3850365L, arm64?.rootfs?.sizeBytes)
        assertEquals("ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea", arm64?.rootfs?.sha256)
    }

    @Test
    fun testParseManifestWithMirrors() {
        val json = """
            {
              "manifestVersion": 1,
              "runtimeVersion": "1.2.27",
              "alpineVersion": "3.21.3",
              "artifacts": {
                "aarch64": {
                  "rootfs": {
                    "url": "https://mirror.yandex.ru/alpine.tar.gz",
                    "mirrors": ["https://dl-cdn.alpinelinux.org/alpine.tar.gz", "https://dotsrc.org/alpine.tar.gz"],
                    "sha256": "ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea",
                    "sizeBytes": 3850365
                  },
                  "opencode": {
                    "url": "https://registry.npmjs.org/opencode.tgz",
                    "mirrors": ["https://registry.npmmirror.com/opencode.tgz"],
                    "sha256": "388b9380d5e203cee9aec071d810a8ab6a717702f94f932ef2446829ec035a92",
                    "sizeBytes": 44518105
                  }
                },
                "x86_64": {
                  "rootfs": {
                    "url": "https://mirror.yandex.ru/alpine-x64.tar.gz",
                    "sha256": "1a694899e406ce55d32334c47ac0b2efb6c06d7e878102d1840892ad44cd5239",
                    "sizeBytes": 3507952
                  },
                  "opencode": {
                    "url": "https://registry.npmjs.org/opencode-x64.tgz",
                    "sha256": "57c28b1787e30590c299d34d90aee9dbb6d8ad59aa83702643fb43c9f2bc9157",
                    "sizeBytes": 44397267
                  }
                }
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        val arm64 = manifest.artifacts["aarch64"]
        assertNotNull(arm64)
        assertEquals(2, arm64?.rootfs?.mirrors?.size)
        assertEquals(3, arm64?.rootfs?.allUrls?.size)
        assertEquals("https://mirror.yandex.ru/alpine.tar.gz", arm64?.rootfs?.allUrls?.get(0))
        assertEquals("https://dl-cdn.alpinelinux.org/alpine.tar.gz", arm64?.rootfs?.allUrls?.get(1))
        assertEquals("https://dotsrc.org/alpine.tar.gz", arm64?.rootfs?.allUrls?.get(2))
        assertEquals(1, arm64?.opencode?.mirrors?.size)
        assertEquals(2, arm64?.opencode?.allUrls?.size)
    }

    @Test
    fun testRejectInvalidSha256() {
        val json = """
            {
              "manifestVersion": 1,
              "runtimeVersion": "1.2.27",
              "alpineVersion": "3.21.3",
              "artifacts": {
                "aarch64": {
                  "rootfs": {
                    "url": "https://test.com/rootfs.tar.gz",
                    "sha256": "invalid_short_hash",
                    "sizeBytes": 1000
                  },
                  "opencode": {
                    "url": "https://test.com/opencode.tgz",
                    "sha256": "388b9380d5e203cee9aec071d810a8ab6a717702f94f932ef2446829ec035a92",
                    "sizeBytes": 1000
                  }
                },
                "x86_64": {
                  "rootfs": {
                    "url": "https://test.com/rootfs.tar.gz",
                    "sha256": "1a694899e406ce55d32334c47ac0b2efb6c06d7e878102d1840892ad44cd5239",
                    "sizeBytes": 1000
                  },
                  "opencode": {
                    "url": "https://test.com/opencode.tgz",
                    "sha256": "57c28b1787e30590c299d34d90aee9dbb6d8ad59aa83702643fb43c9f2bc9157",
                    "sizeBytes": 1000
                  }
                }
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun testRejectMissingArchitecture() {
        val json = """
            {
              "manifestVersion": 1,
              "runtimeVersion": "1.2.27",
              "alpineVersion": "3.21.3",
              "artifacts": {
                "aarch64": {
                  "rootfs": {
                    "url": "https://test.com/rootfs.tar.gz",
                    "sha256": "ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea",
                    "sizeBytes": 1000
                  },
                  "opencode": {
                    "url": "https://test.com/opencode.tgz",
                    "sha256": "388b9380d5e203cee9aec071d810a8ab6a717702f94f932ef2446829ec035a92",
                    "sizeBytes": 1000
                  }
                }
              }
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun testParseProductionAssetManifest() {
        var current: File? = File(".").canonicalFile
        var assetFile: File? = null
        while (current != null) {
            val candidate = File(current, "app/src/main/assets/local-runtime-manifest.json")
            if (candidate.exists()) {
                assetFile = candidate
                break
            }
            current = current.parentFile
        }

        assertNotNull("Production local-runtime-manifest.json asset file must exist", assetFile)
        val json = assetFile!!.readText(Charsets.UTF_8)
        val result = parser.parse(json)
        assertTrue("Production manifest must be parsed successfully without JSON errors: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals(1, manifest.manifestVersion)
        assertTrue(manifest.artifacts.containsKey("aarch64"))
        assertTrue(manifest.artifacts.containsKey("x86_64"))
    }
}
