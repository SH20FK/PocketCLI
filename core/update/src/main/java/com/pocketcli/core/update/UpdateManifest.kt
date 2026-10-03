package com.pocketcli.core.update

import kotlinx.serialization.Serializable

enum class UpdateChannel(val id: String, val displayName: String) {
    STABLE("stable", "Стабильный канал (Stable)"),
    BETA("beta", "Бета канал (Beta / Pre-release)")
}

@Serializable
data class ApkAsset(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String
)

@Serializable
data class UpdateManifest(
    val schemaVersion: Int = 1,
    val channel: String = "stable",
    val versionName: String,
    val versionCode: Long,
    val tag: String,
    val publishedAt: String = "",
    val minSdk: Int = 28,
    val minSupportedVersionCode: Long = 1,
    val critical: Boolean = false,
    val apk: ApkAsset,
    val notes: List<String> = emptyList()
)
