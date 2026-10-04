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

        var releases: List<GitHubReleaseDto> = emptyList()
        var lastError: String? = null

        // 1. Try GitHub REST API
        try {
            val req = Request.Builder()
                .url(releasesUrl)
                .header("User-Agent", "PocketCLI-Android-Updater")
                .header("Accept", "application/vnd.github+json")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    releases = json.decodeFromString<List<GitHubReleaseDto>>(body)
                } else {
                    lastError = "REST API HTTP ${resp.code}: ${resp.message}"
                }
            }
        } catch (e: Exception) {
            lastError = "REST API: ${e.localizedMessage ?: "Ошибка сети"}"
        }

        // 2. If REST API failed or returned empty (e.g. rate limit HTTP 403/429), fallback to public Atom feed
        if (releases.isEmpty()) {
            val atomReleases = fetchFromAtomFeed(repoOwner, repoName)
            if (atomReleases.isNotEmpty()) {
                releases = atomReleases
            } else {
                val err = lastError
                if (err != null) {
                    return@withContext ManifestFetchResult.NetworkError(err)
                }
            }
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

    private fun fetchFromAtomFeed(repoOwner: String, repoName: String): List<GitHubReleaseDto> {
        val atomUrl = "https://github.com/$repoOwner/$repoName/releases.atom"
        return try {
            val req = Request.Builder()
                .url(atomUrl)
                .header("User-Agent", "PocketCLI-Android-Updater")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty()
                parseAtomFeed(body, repoOwner, repoName)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseAtomFeed(atomXml: String, repoOwner: String, repoName: String): List<GitHubReleaseDto> {
        val entryRegex = Regex("<entry>([\\s\\S]*?)</entry>")
        val tagRegex = Regex("""/releases/tag/([^"/]+)""")
        val updatedRegex = Regex("""<updated>([^<]+)</updated>""")

        val results = mutableListOf<GitHubReleaseDto>()
        for (match in entryRegex.findAll(atomXml)) {
            val entryContent = match.groupValues[1]
            val tagMatch = tagRegex.find(entryContent) ?: continue
            val tag = tagMatch.groupValues[1]
            val updated = updatedRegex.find(entryContent)?.groupValues?.get(1)

            val isPrerelease = tag.contains(Regex("-(beta|alpha|rc)", RegexOption.IGNORE_CASE))
            val assetUrl = "https://github.com/$repoOwner/$repoName/releases/download/$tag/update.json"

            results.add(
                GitHubReleaseDto(
                    tagName = tag,
                    draft = false,
                    prerelease = isPrerelease,
                    publishedAt = updated,
                    assets = listOf(
                        GitHubAssetDto(
                            name = "update.json",
                            browserDownloadUrl = assetUrl
                        )
                    )
                )
            )
        }
        return results
    }
}
