package com.pocketcli.runtime.local.manifest

import kotlinx.serialization.Serializable

@Serializable
data class RuntimeManifest(
    val manifestVersion: Int,
    val runtimeVersion: String,
    val alpineVersion: String,
    val artifacts: Map<String, ArchArtifacts>
)

@Serializable
data class ArchArtifacts(
    val rootfs: ArtifactInfo,
    val opencode: ArtifactInfo
)

@Serializable
data class ArtifactInfo(
    val url: String,
    val sha256: String,
    val sizeBytes: Long
) {
    fun isValid(): Boolean {
        return url.isNotBlank() &&
                sizeBytes > 0 &&
                sha256.length == 64 &&
                sha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
}
