package com.pocketcli.data.opencode.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@OptIn(ExperimentalSerializationApi::class)
class OpenCodeApiClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildUrl(path: String): String {
        val base = baseUrlProvider().trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$base$cleanPath"
    }

    suspend fun getHealth(): Result<OpenCodeHealthDto> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl("/global/health"))
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string().orEmpty()
                json.decodeFromString<OpenCodeHealthDto>(body)
            }
        }
    }

    suspend fun createSession(title: String, directory: String? = null): Result<OpenCodeSessionDto> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(OpenCodeCreateSessionRequest(title = title))
                .toRequestBody(jsonMediaType)
            val rawUrl = buildUrl("/session")
            val targetUrl = if (!directory.isNullOrBlank()) {
                val httpUrl = rawUrl.toHttpUrlOrNull()
                if (httpUrl != null) {
                    httpUrl.newBuilder().addQueryParameter("directory", directory).build().toString()
                } else {
                    "$rawUrl?directory=${java.net.URLEncoder.encode(directory, "UTF-8")}"
                }
            } else {
                rawUrl
            }
            val request = Request.Builder()
                .url(targetUrl)
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
                val respBody = response.body?.string().orEmpty()
                json.decodeFromString<OpenCodeSessionDto>(respBody)
            }
        }
    }

    suspend fun listSessions(): Result<List<OpenCodeSessionDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl("/session"))
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
                val respBody = response.body?.string().orEmpty()
                json.decodeFromString<List<OpenCodeSessionDto>>(respBody)
            }
        }
    }

    suspend fun getProviders(): Result<OpenCodeProvidersResponseDto> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl("/provider"))
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
                val respBody = response.body?.string().orEmpty()
                json.decodeFromString<OpenCodeProvidersResponseDto>(respBody)
            }
        }
    }

    suspend fun sendMessage(
        sessionId: String,
        promptText: String,
        model: OpenCodeModelInput? = null
    ): Result<OpenCodeReconcileMessageDto?> = withContext(Dispatchers.IO) {
        runCatching {
            val reqPayload = buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", promptText)
                    })
                })
                if (model != null) {
                    put("model", buildJsonObject {
                        put("providerID", model.providerID)
                        put("modelID", model.modelID)
                    })
                }
            }
            val body = reqPayload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(buildUrl("/session/$sessionId/message"))
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
                val respBody = response.body?.string().orEmpty()
                runCatching { json.decodeFromString<OpenCodeReconcileMessageDto>(respBody) }.getOrNull()
            }
        }
    }

    suspend fun abortSession(sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val emptyBody = "".toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(buildUrl("/session/$sessionId/abort"))
                .post(emptyBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
                val respBody = response.body?.string().orEmpty()
                respBody.toBooleanStrictOrNull() ?: true
            }
        }
    }

    suspend fun replyPermission(requestId: String, reply: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reqPayload = buildJsonObject {
                put("response", reply)
            }
            val body = reqPayload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(buildUrl("/permission/$requestId/reply"))
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
            }
        }
    }

    suspend fun getTodos(sessionId: String): Result<List<OpenCodeTodoDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl("/session/$sessionId/todo"))
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
                val respBody = response.body?.string().orEmpty()
                json.decodeFromString<List<OpenCodeTodoDto>>(respBody)
            }
        }
    }

    suspend fun getMessages(sessionId: String): Result<List<OpenCodeReconcileMessageDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl("/session/$sessionId/message"))
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    throw IOException("HTTP ${response.code}: ${response.message}${if (errorBody.isNotEmpty()) " - $errorBody" else ""}")
                }
                val respBody = response.body?.string().orEmpty()
                json.decodeFromString<List<OpenCodeReconcileMessageDto>>(respBody)
            }
        }
    }
}

@Serializable
data class OpenCodeTodoDto(
    val content: String,
    val status: String,
    val priority: String = "medium"
)
