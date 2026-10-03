package com.pocketcli.runtime.local.manifest

import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManifestParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(jsonContent: String): Result<RuntimeManifest> = runCatching {
        val manifest = json.decodeFromString<RuntimeManifest>(jsonContent)

        require(manifest.manifestVersion >= 1) { "Invalid manifestVersion: ${manifest.manifestVersion}" }
        require(manifest.runtimeVersion.isNotBlank()) { "runtimeVersion cannot be blank" }
        require(manifest.alpineVersion.isNotBlank()) { "alpineVersion cannot be blank" }

        val aarch64 = manifest.artifacts["aarch64"]
        requireNotNull(aarch64) { "Manifest must contain 'aarch64' artifacts" }
        require(aarch64.rootfs.isValid()) { "Invalid aarch64 rootfs artifact" }
        require(aarch64.opencode.isValid()) { "Invalid aarch64 opencode artifact" }

        val x86_64 = manifest.artifacts["x86_64"]
        requireNotNull(x86_64) { "Manifest must contain 'x86_64' artifacts" }
        require(x86_64.rootfs.isValid()) { "Invalid x86_64 rootfs artifact" }
        require(x86_64.opencode.isValid()) { "Invalid x86_64 opencode artifact" }

        manifest
    }
}
