package com.pocketcli.core.update

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor() {

    fun isUpdateAvailable(
        currentVersionCode: Long,
        manifest: UpdateManifest,
        currentVersionName: String? = null
    ): Boolean {
        if (manifest.versionCode > currentVersionCode) {
            return true
        }
        if (manifest.versionCode == currentVersionCode && !currentVersionName.isNullOrBlank()) {
            val normCurrent = currentVersionName.trim().removePrefix("v")
            val normManifest = manifest.versionName.trim().removePrefix("v")
            if (normCurrent.isNotEmpty() && normManifest.isNotEmpty() && normCurrent != normManifest) {
                return true
            }
        }
        return false
    }

    fun isCompatible(currentVersionCode: Long, manifest: UpdateManifest): Boolean {
        return currentVersionCode >= manifest.minSupportedVersionCode
    }

    fun computeVersionCode(major: Int, minor: Int, patch: Int): Long {
        return major * 1_000_000L + minor * 1_000L + patch.toLong()
    }

    fun shouldNotify(manifest: UpdateManifest, skippedTag: String? = null): Boolean {
        if (skippedTag != null && manifest.tag == skippedTag && !manifest.critical) {
            return false
        }
        return true
    }
}
