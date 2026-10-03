package com.pocketcli.data.opencode.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OpenCodeApiClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
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

    suspend fun createSession(title: String): Result<OpenCodeSessionDto> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(OpenCodeCreateSessionRequest(title = title))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(buildUrl("/session"))
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
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
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val respBody = response.body?.string().orEmpty()
                json.decodeFromString<List<OpenCodeSessionDto>>(respBody)
            }
        }
    }

    suspend fun sendMessage(
        sessionId: String,
        promptText: String,
        model: OpenCodeModelInput? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reqPayload = OpenCodeSendMessageRequest(
                parts = listOf(OpenCodeTextPartInput(type = "text", text = promptText)),
                model = model
            )
            val body = json.encodeToString(reqPayload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(buildUrl("/session/$sessionId/message"))
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
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
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val respBody = response.body?.string().orEmpty()
                respBody.toBooleanStrictOrNull() ?: true
            }
        }
    }

    suspend fun replyPermission(requestId: String, reply: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = OpenCodePermissionReplyRequest(reply = reply)
            val body = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(buildUrl("/permission/$requestId/reply"))
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
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
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val respBody = response.body?.string().orEmpty()
                json.decodeFromString<List<OpenCodeReconcileMessageDto>>(respBody)
            }
        }
    }
}
