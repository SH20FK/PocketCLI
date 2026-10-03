package com.pocketcli.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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
        channel: UpdateChannel = UpdateChannel.STABLE
    ): Result<UpdateManifest> = withContext(Dispatchers.IO) {
        val targetUrl = if (channel == UpdateChannel.BETA) {
            "https://raw.githubusercontent.com/$repoOwner/$repoName/main/update.json"
        } else {
            "https://github.com/$repoOwner/$repoName/releases/latest/download/update.json"
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "PocketCLI-Android-Updater")
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: Не удалось загрузить манифест обновления")
                    )
                }
                val bodyString = response.body?.string().orEmpty()
                if (bodyString.isBlank()) {
                    return@withContext Result.failure(IOException("Пустой ответ от сервера релизов"))
                }
                val manifest = json.decodeFromString<UpdateManifest>(bodyString)
                Result.success(manifest)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
