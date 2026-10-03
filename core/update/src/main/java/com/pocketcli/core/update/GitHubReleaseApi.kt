package com.pocketcli.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0
)

sealed interface ManifestFetchResult {
    data class Found(val manifest: UpdateManifest) : ManifestFetchResult
    data class NoRelease(val channel: UpdateChannel) : ManifestFetchResult
    data class MissingAsset(val tag: String, val asset: String) : ManifestFetchResult
    data class InvalidManifest(val tag: String, val reason: String) : ManifestFetchResult
    data class NetworkError(val reason: String) : ManifestFetchResult
}

@Singleton
class GitHubReleaseApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetchLatestManifest(
        repoOwner: String = "SH20FK",
        repoName: String = "PocketCLI",
        channel: UpdateChannel = UpdateChannel.STABLE,
        apiBaseUrl: String = "https://api.github.com"
    ): ManifestFetchResult = withContext(Dispatchers.IO) {
        val releasesUrl = "$apiBaseUrl/repos/$repoOwner/$repoName/releases?per_page=20"

        val releases: List<GitHubReleaseDto> = try {
            val req = Request.Builder()
                .url(releasesUrl)
                .header("User-Agent", "PocketCLI-Android-Updater")
                .header("Accept", "application/vnd.github+json")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext ManifestFetchResult.NetworkError("HTTP ${resp.code}: ${resp.message}")
                }
                val body = resp.body?.string().orEmpty()
                json.decodeFromString<List<GitHubReleaseDto>>(body)
            }
        } catch (e: Exception) {
            return@withContext ManifestFetchResult.NetworkError(e.localizedMessage ?: "Ошибка сети при запросе релизов")
        }

        // Filter releases:
        // - ignore drafts
        // - if STABLE: ignore prerelease
        // - if BETA: allow both stable and prereleases
        val candidates = releases.filter { release ->
            if (release.draft) return@filter false
            if (channel == UpdateChannel.STABLE && release.prerelease) return@filter false
            true
        }

        if (candidates.isEmpty()) {
            return@withContext ManifestFetchResult.NoRelease(channel)
        }

        // Take newest candidate
        val selectedRelease = candidates.first()
        val updateJsonAsset = selectedRelease.assets.firstOrNull { it.name == "update.json" }
            ?: return@withContext ManifestFetchResult.MissingAsset(selectedRelease.tagName, "update.json")

        // Download update.json
        val manifest: UpdateManifest = try {
            val req = Request.Builder()
                .url(updateJsonAsset.browserDownloadUrl)
                .header("User-Agent", "PocketCLI-Android-Updater")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext ManifestFetchResult.NetworkError("HTTP ${resp.code}: Не удалось скачать update.json")
                }
                val body = resp.body?.string().orEmpty()
                json.decodeFromString<UpdateManifest>(body)
            }
        } catch (e: Exception) {
            return@withContext ManifestFetchResult.InvalidManifest(selectedRelease.tagName, e.localizedMessage ?: "Ошибка парсинга update.json")
        }

        // Validate tag match (allowing prefix differences like v1.0.2 vs 1.0.2)
        val normTag1 = manifest.tag.removePrefix("v")
        val normTag2 = selectedRelease.tagName.removePrefix("v")
        if (normTag1 != normTag2) {
            return@withContext ManifestFetchResult.InvalidManifest(
                selectedRelease.tagName,
                "Тег в манифесте (${manifest.tag}) не совпадает с тегом релиза (${selectedRelease.tagName})"
            )
        }

        ManifestFetchResult.Found(manifest)
    }
}
