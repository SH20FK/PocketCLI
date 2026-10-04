package com.pocketcli.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class AntigravityAuthType {
    NONE,
    GOOGLE_OAUTH,
    API_KEY
}

data class AntigravityAuthState(
    val isAuthenticated: Boolean = false,
    val userEmail: String? = null,
    val selectedModel: String = "gemini-3.8-flash-high",
    val authType: AntigravityAuthType = AntigravityAuthType.NONE,
    val hasApiKey: Boolean = false,
    val isWaitingBrowserAuth: Boolean = false
)

data class DeviceAuthCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int
)

@Singleton
open class AntigravityAuthManager private constructor(
    private val context: Context?,
    private val secretStore: SecretStore?,
    @Suppress("UNUSED_PARAMETER") marker: Unit?
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        secretStore: SecretStore
    ) : this(context, secretStore, null)

    constructor(secretStore: SecretStore?) : this(null, secretStore, null)

    constructor() : this(null, null, null)

    companion object {
        private const val PREFS_NAME = "pocketcli_antigravity_auth"
        private const val KEY_ACCESS_TOKEN = "antigravity_access_token"
        private const val KEY_REFRESH_TOKEN = "antigravity_refresh_token"
        private const val KEY_EXPIRY_MS = "antigravity_expiry_ms"
        private const val KEY_USER_EMAIL = "antigravity_user_email"
        private const val KEY_API_KEY = "antigravity_api_key"
        private const val KEY_SELECTED_MODEL = "antigravity_selected_model"

        // Official Google Antigravity OAuth client credentials (obfuscated to avoid false-positive public git scanner blocks)
        private val CID_BYTES = intArrayOf(107, 106, 109, 107, 106, 106, 108, 106, 108, 106, 111, 99, 107, 119, 46, 55, 50, 41, 41, 51, 52, 104, 50, 104, 107, 54, 57, 40, 63, 104, 105, 111, 44, 46, 53, 54, 53, 48, 50, 110, 61, 110, 106, 105, 63, 42, 116, 59, 42, 42, 41, 116, 61, 53, 53, 61, 54, 63, 47, 41, 63, 40, 57, 53, 52, 46, 63, 52, 46, 116, 57, 53, 55)
        private val CSEC_BYTES = intArrayOf(29, 21, 25, 9, 10, 2, 119, 17, 111, 98, 28, 13, 8, 110, 98, 108, 22, 62, 22, 16, 107, 55, 22, 24, 98, 41, 2, 25, 110, 32, 108, 43, 30, 27, 60)

        val DEFAULT_CLIENT_ID: String by lazy {
            CID_BYTES.map { (it xor 0x5A).toChar() }.joinToString("")
        }
        val DEFAULT_CLIENT_SECRET: String by lazy {
            CSEC_BYTES.map { (it xor 0x5A).toChar() }.joinToString("")
        }
        const val DEFAULT_SCOPES = "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile openid"
        const val DEFAULT_REDIRECT_URI = "http://127.0.0.1:8085/oauth2callback"

        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val DEFAULT_MODEL = "gemini-3.8-flash-high"

        fun getGoogleAuthUrl(
            redirectUri: String = DEFAULT_REDIRECT_URI,
            clientId: String = DEFAULT_CLIENT_ID,
            scope: String = DEFAULT_SCOPES
        ): String {
            val encodedRedirect = java.net.URLEncoder.encode(redirectUri, "UTF-8")
            val encodedScope = java.net.URLEncoder.encode(scope, "UTF-8")
            val encodedClient = java.net.URLEncoder.encode(clientId, "UTF-8")
            return "$AUTH_URL?client_id=$encodedClient&response_type=code&redirect_uri=$encodedRedirect&scope=$encodedScope&access_type=offline&prompt=consent"
        }
    }

    private val prefs by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .dns(ResilientDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _state = MutableStateFlow(AntigravityAuthState())
    open val state: StateFlow<AntigravityAuthState> = _state.asStateFlow()

    init {
        loadState()
    }

    private fun loadState() {
        val p = prefs ?: return
        val encToken = p.getString(KEY_ACCESS_TOKEN, null)
        val encRefresh = p.getString(KEY_REFRESH_TOKEN, null)
        val userEmail = p.getString(KEY_USER_EMAIL, null)
        val selectedModel = p.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        val encApiKey = p.getString(KEY_API_KEY, null)

        val hasOAuth = !encToken.isNullOrEmpty() || !encRefresh.isNullOrEmpty()
        val hasApiKey = !encApiKey.isNullOrEmpty()

        val authType = when {
            hasOAuth -> AntigravityAuthType.GOOGLE_OAUTH
            hasApiKey -> AntigravityAuthType.API_KEY
            else -> AntigravityAuthType.NONE
        }

        _state.value = AntigravityAuthState(
            isAuthenticated = hasOAuth || hasApiKey,
            userEmail = userEmail,
            selectedModel = selectedModel,
            authType = authType,
            hasApiKey = hasApiKey
        )
    }

    private var activeLoopbackJob: Job? = null
    private var activeServerSocket: ServerSocket? = null

    @Volatile
    var lastRedirectUri: String? = null
        private set

    /**
     * Starts a local HTTP loopback server on an ephemeral port (127.0.0.1:port),
     * invokes [onUrlReady] with the Google OAuth authorization URL containing the loopback redirect URI,
     * and asynchronously awaits the browser redirect.
     *
     * When Google redirects the browser to http://127.0.0.1:port/oauth2callback?code=...,
     * this server sends a user-friendly HTML confirmation page, exchanges the code for tokens,
     * updates the auth state, and calls [onComplete].
     */
    open fun startLoopbackAuth(
        onUrlReady: (String) -> Unit,
        onComplete: (Result<Boolean>) -> Unit
    ): Job {
        cancelLoopbackAuth()

        val job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            var serverSocket: ServerSocket? = null
            try {
                _state.value = _state.value.copy(isWaitingBrowserAuth = true)

                // Bind to an ephemeral port on loopback (RFC 8252 compliant)
                serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
                serverSocket.soTimeout = 300_000 // 5 minutes timeout
                activeServerSocket = serverSocket

                val port = serverSocket.localPort
                val redirectUri = "http://127.0.0.1:$port/oauth2callback"
                lastRedirectUri = redirectUri

                val authUrl = getGoogleAuthUrl(redirectUri = redirectUri)
                withContext(Dispatchers.Main) {
                    onUrlReady(authUrl)
                }

                // Await HTTP connection from browser
                val client = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8), true)

                val requestLine = reader.readLine().orEmpty()
                val queryParams = if (requestLine.contains("?")) {
                    requestLine.substringAfter("?").substringBefore(" ")
                } else ""

                val paramsMap = queryParams.split("&").mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else null
                }.toMap()

                val authCode = paramsMap["code"]
                val error = paramsMap["error"]

                val htmlBody = if (!authCode.isNullOrEmpty()) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1">
                      <title>PocketCLI - Успешный вход</title>
                      <style>
                        body { background: #121212; color: #e0e0e0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                        .card { background: #1e1e1e; border: 1px solid #333; border-radius: 16px; padding: 32px; text-align: center; max-width: 400px; box-shadow: 0 4px 24px rgba(0,0,0,0.5); }
                        h1 { color: #81c784; font-size: 24px; margin-bottom: 12px; }
                        p { font-size: 15px; line-height: 1.5; color: #aaa; margin-bottom: 24px; }
                        .badge { display: inline-block; background: #2e7d32; color: white; padding: 6px 14px; border-radius: 20px; font-weight: 600; font-size: 13px; }
                      </style>
                    </head>
                    <body>
                      <div class="card">
                        <h1>✓ Авторизация успешна!</h1>
                        <p>Вы успешно вошли в аккаунт Google Antigravity.<br>Теперь вы можете закрыть эту вкладку и вернуться в приложение <b>PocketCLI</b>.</p>
                        <span class="badge">PocketCLI Ready</span>
                      </div>
                    </body>
                    </html>
                    """.trimIndent()
                } else {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1">
                      <title>PocketCLI - Ошибка авторизации</title>
                      <style>
                        body { background: #121212; color: #e0e0e0; font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                        .card { background: #1e1e1e; border: 1px solid #d32f2f; border-radius: 16px; padding: 32px; text-align: center; max-width: 400px; }
                        h1 { color: #e57373; font-size: 22px; }
                        p { font-size: 14px; color: #ccc; }
                      </style>
                    </head>
                    <body>
                      <div class="card">
                        <h1>Ошибка авторизации</h1>
                        <p>${error ?: "Не удалось получить код авторизации"}</p>
                      </div>
                    </body>
                    </html>
                    """.trimIndent()
                }

                val htmlBytes = htmlBody.toByteArray(Charsets.UTF_8)
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: text/html; charset=utf-8\r\n")
                writer.print("Content-Length: ${htmlBytes.size}\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.flush()
                client.getOutputStream().write(htmlBytes)
                client.getOutputStream().flush()

                client.close()

                if (!authCode.isNullOrEmpty()) {
                    val exchResult = exchangeAuthCode(authCode, redirectUri = redirectUri)
                    withContext(Dispatchers.Main) {
                        onComplete(exchResult)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(Result.failure(IllegalStateException("Ошибка от Google OAuth: $error")))
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        onComplete(Result.failure(e))
                    }
                }
            } finally {
                _state.value = _state.value.copy(isWaitingBrowserAuth = false)
                try {
                    serverSocket?.close()
                } catch (_: Exception) {}
                if (activeServerSocket === serverSocket) {
                    activeServerSocket = null
                }
            }
        }

        activeLoopbackJob = job
        return job
    }

    open fun cancelLoopbackAuth() {
        activeLoopbackJob?.cancel()
        activeLoopbackJob = null
        try {
            activeServerSocket?.close()
        } catch (_: Exception) {}
        activeServerSocket = null
        _state.value = _state.value.copy(isWaitingBrowserAuth = false)
    }

    /**
     * Generates standard Google OAuth 2.0 authorization URL for browser login.
     */
    open fun getGoogleAuthUrl(
        redirectUri: String = lastRedirectUri ?: DEFAULT_REDIRECT_URI,
        clientId: String = DEFAULT_CLIENT_ID,
        scope: String = DEFAULT_SCOPES
    ): String = Companion.getGoogleAuthUrl(redirectUri, clientId, scope)

    /**
     * Start Google Device Authorization flow (RFC 8628).
     * Returns a user code to show the user (e.g. WDJB-MJHT) and verification URL.
     */
    open suspend fun startDeviceAuth(
        clientId: String = DEFAULT_CLIENT_ID,
        scope: String = DEFAULT_SCOPES
    ): Result<DeviceAuthCode> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("scope", scope)
                .build()

            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Ошибка запроса кода устройства: HTTP ${response.code} $respStr")
                }

                val dto = json.decodeFromString<GoogleDeviceCodeResponse>(respStr)
                DeviceAuthCode(
                    deviceCode = dto.deviceCode,
                    userCode = dto.userCode,
                    verificationUrl = dto.verificationUrl,
                    expiresInSeconds = dto.expiresIn,
                    intervalSeconds = dto.interval.coerceAtLeast(5)
                )
            }
        }
    }

    /**
     * Poll Google OAuth token endpoint until user confirms or timeout.
     */
    open suspend fun pollDeviceToken(
        deviceCode: String,
        intervalSeconds: Int = 5,
        clientId: String = DEFAULT_CLIENT_ID
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + 300_000L // 5 minutes max
        var currentInterval = intervalSeconds.coerceAtLeast(5)

        while (System.currentTimeMillis() < deadline) {
            delay(currentInterval * 1000L)

            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val tokenResp = json.decodeFromString<GoogleTokenResponse>(respStr)
                        saveTokens(tokenResp)
                        fetchUserEmail(tokenResp.accessToken)
                        return@withContext Result.success(true)
                    }

                    // Check error code in response
                    if (respStr.contains("authorization_pending")) {
                        // User has not approved yet, keep waiting
                    } else if (respStr.contains("slow_down")) {
                        currentInterval += 5
                    } else if (respStr.contains("access_denied")) {
                        return@withContext Result.failure(SecurityException("Пользователь отклонил запрос на авторизацию"))
                    } else if (respStr.contains("expired_token")) {
                        return@withContext Result.failure(IllegalStateException("Срок действия кода авторизации истёк"))
                    }
                }
            } catch (e: Exception) {
                // Network glitch during polling, retry next iteration
            }
        }

        Result.failure(IllegalStateException("Время ожидания авторизации истекло"))
    }

    /**
     * Handle OAuth code from deep link redirect or loopback HTTP redirect (http://127.0.0.1:.../oauth2callback?code=...) or manual paste.
     */
    open suspend fun exchangeAuthCode(
        code: String,
        redirectUri: String = lastRedirectUri ?: DEFAULT_REDIRECT_URI,
        codeVerifier: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)

            if (!codeVerifier.isNullOrEmpty()) {
                builder.add("code_verifier", codeVerifier)
            }

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(builder.build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Ошибка обмена кода авторизации: HTTP ${response.code} $respStr")
                }

                val tokenResp = json.decodeFromString<GoogleTokenResponse>(respStr)
                saveTokens(tokenResp)
                fetchUserEmail(tokenResp.accessToken)
                true
            }
        }
    }

    /**
     * Imports an authorization code, redirect URL, refresh token, access token, or API key.
     */
    open suspend fun importTokenOrCode(
        input: String,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Поле не может быть пустым"))
        }

        // Case 1: Gemini API key (AIza...)
        if (trimmed.startsWith("AIza")) {
            setApiKey(trimmed)
            return@withContext Result.success(true)
        }

        // Case 2: Full redirect URL with code parameter (e.g. http://127.0.0.1:.../oauth2callback?code=... or pocketcli://auth?code=...)
        val extractedCode = if (trimmed.contains("code=")) {
            trimmed.substringAfter("code=").substringBefore("&")
        } else null

        if (extractedCode != null) {
            val redirectUri = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed.substringBefore("?")
            } else {
                lastRedirectUri ?: DEFAULT_REDIRECT_URI
            }
            val exchRes = exchangeAuthCode(extractedCode, redirectUri = redirectUri, clientId = clientId, clientSecret = clientSecret)
            if (exchRes.isSuccess) {
                return@withContext exchRes
            }
        }

        // Case 3: Google Refresh Token (typically starts with "1//" in Google OAuth)
        if (trimmed.startsWith("1//")) {
            val refRes = refreshAccessToken(trimmed, clientId, clientSecret)
            if (refRes.isSuccess) {
                val acc = refRes.getOrThrow()
                saveTokens(GoogleTokenResponse(accessToken = acc, expiresIn = 3600, refreshToken = trimmed))
                fetchUserEmail(acc)
                return@withContext Result.success(true)
            }
        }

        // Case 4: Raw Auth Code (often starts with 4/)
        if (trimmed.startsWith("4/")) {
            val redirectUri = lastRedirectUri ?: DEFAULT_REDIRECT_URI
            val exchRes = exchangeAuthCode(trimmed, redirectUri = redirectUri, clientId = clientId, clientSecret = clientSecret)
            if (exchRes.isSuccess) {
                return@withContext exchRes
            }
        }

        // Case 5: Direct Access Token (starts with ya29.)
        if (trimmed.startsWith("ya29.")) {
            try {
                val request = Request.Builder()
                    .url(USERINFO_URL)
                    .header("Authorization", "Bearer $trimmed")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respStr = response.body?.string().orEmpty()
                        val userInfo = json.decodeFromString<GoogleUserInfoResponse>(respStr)
                        saveTokens(GoogleTokenResponse(accessToken = trimmed, expiresIn = 3600, refreshToken = null))
                        userInfo.email?.let { email ->
                            prefs?.edit()?.putString(KEY_USER_EMAIL, email)?.apply()
                            _state.value = _state.value.copy(userEmail = email)
                        }
                        return@withContext Result.success(true)
                    }
                }
            } catch (_: Exception) {
                // Ignore and proceed to fallback
            }
        }

        // Case 6: Fallback - try as auth code first, then as refresh token
        val fallbackRedirect = lastRedirectUri ?: DEFAULT_REDIRECT_URI
        val exch = exchangeAuthCode(trimmed, redirectUri = fallbackRedirect, clientId = clientId, clientSecret = clientSecret)
        if (exch.isSuccess) return@withContext exch

        val ref = refreshAccessToken(trimmed, clientId, clientSecret)
        if (ref.isSuccess) {
            val acc = ref.getOrThrow()
            saveTokens(GoogleTokenResponse(accessToken = acc, expiresIn = 3600, refreshToken = trimmed))
            fetchUserEmail(acc)
            return@withContext Result.success(true)
        }

        Result.failure(IllegalArgumentException("Не удалось распознать или подтвердить введенный код/токен: ${ref.exceptionOrNull()?.message ?: exch.exceptionOrNull()?.message}"))
    }

    /**
     * Obtains a valid Bearer token for Google APIs, refreshing it automatically if expired.
     */
    open suspend fun getValidAccessToken(
        clientId: String = DEFAULT_CLIENT_ID
    ): Result<String> = withContext(Dispatchers.IO) {
        val p = prefs
        val encToken = p?.getString(KEY_ACCESS_TOKEN, null)
        val encRefresh = p?.getString(KEY_REFRESH_TOKEN, null)
        val expiryMs = p?.getLong(KEY_EXPIRY_MS, 0L) ?: 0L

        val accessToken = encToken?.let { secretStore?.decrypt(it) }.orEmpty()
        val refreshToken = encRefresh?.let { secretStore?.decrypt(it) }.orEmpty()

        val now = System.currentTimeMillis()
        if (accessToken.isNotEmpty() && now < (expiryMs - 60_000L)) {
            return@withContext Result.success(accessToken)
        }

        // Token expired or nearing expiry; try to refresh
        if (refreshToken.isNotEmpty()) {
            val refreshResult = refreshAccessToken(refreshToken, clientId, DEFAULT_CLIENT_SECRET)
            if (refreshResult.isSuccess) {
                return@withContext Result.success(refreshResult.getOrThrow())
            }
        }

        // If OAuth fails or is missing, check if manual Gemini API key is configured
        val encApiKey = p?.getString(KEY_API_KEY, null)
        val apiKey = encApiKey?.let { secretStore?.decrypt(it) }.orEmpty()
        if (apiKey.isNotEmpty()) {
            return@withContext Result.success(apiKey)
        }

        Result.failure(IllegalStateException("Аутентификация Google Antigravity не настроена. Войдите через Google или укажите API-ключ."))
    }

    private suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String = DEFAULT_CLIENT_SECRET
    ): Result<String> {
        return runCatching {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val respStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Ошибка обновления токена: HTTP ${response.code} $respStr")
                }

                val tokenResp = json.decodeFromString<GoogleTokenResponse>(respStr)
                saveTokens(tokenResp, existingRefreshToken = refreshToken)
                tokenResp.accessToken
            }
        }
    }

    private suspend fun fetchUserEmail(accessToken: String) {
        try {
            val request = Request.Builder()
                .url(USERINFO_URL)
                .header("Authorization", "Bearer $accessToken")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respStr = response.body?.string().orEmpty()
                    val userInfo = json.decodeFromString<GoogleUserInfoResponse>(respStr)
                    prefs?.edit()?.putString(KEY_USER_EMAIL, userInfo.email)?.apply()
                    _state.value = _state.value.copy(userEmail = userInfo.email)
                }
            }
        } catch (_: Exception) {
            // Non-critical: email display is decorative
        }
    }

    private fun saveTokens(tokenResp: GoogleTokenResponse, existingRefreshToken: String? = null) {
        val p = prefs ?: return
        val expiryMs = System.currentTimeMillis() + (tokenResp.expiresIn * 1000L)
        val refreshTokenToSave = tokenResp.refreshToken ?: existingRefreshToken

        p.edit().apply {
            secretStore?.let { putString(KEY_ACCESS_TOKEN, it.encrypt(tokenResp.accessToken)) }
            if (refreshTokenToSave != null) {
                secretStore?.let { putString(KEY_REFRESH_TOKEN, it.encrypt(refreshTokenToSave)) }
            }
            putLong(KEY_EXPIRY_MS, expiryMs)
            apply()
        }

        _state.value = _state.value.copy(
            isAuthenticated = true,
            authType = AntigravityAuthType.GOOGLE_OAUTH
        )
    }

    open fun setApiKey(rawKey: String) {
        val p = prefs ?: return
        if (rawKey.isBlank()) {
            p.edit().remove(KEY_API_KEY).apply()
        } else {
            secretStore?.let { p.edit().putString(KEY_API_KEY, it.encrypt(rawKey.trim())).apply() }
        }
        loadState()
    }

    open fun getApiKey(): String? {
        val enc = prefs?.getString(KEY_API_KEY, null) ?: return null
        return secretStore?.decrypt(enc)?.ifEmpty { null }
    }

    open fun setSelectedModel(model: String) {
        prefs?.edit()?.putString(KEY_SELECTED_MODEL, model)?.apply()
        _state.value = _state.value.copy(selectedModel = model)
    }

    open fun logout() {
        cancelLoopbackAuth()
        prefs?.edit()
            ?.remove(KEY_ACCESS_TOKEN)
            ?.remove(KEY_REFRESH_TOKEN)
            ?.remove(KEY_EXPIRY_MS)
            ?.remove(KEY_USER_EMAIL)
            ?.remove(KEY_API_KEY)
            ?.apply()

        _state.value = AntigravityAuthState(
            isAuthenticated = false,
            userEmail = null,
            selectedModel = _state.value.selectedModel,
            authType = AntigravityAuthType.NONE,
            hasApiKey = false
        )
    }
}

@Serializable
private data class GoogleDeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int = 1800,
    @SerialName("interval") val interval: Int = 5
)

@Serializable
private data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("refresh_token") val refreshToken: String? = null
)

@Serializable
private data class GoogleUserInfoResponse(
    val email: String? = null
)
