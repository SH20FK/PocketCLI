package com.pocketcli.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
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
    val selectedModel: String = "gemini-2.5-pro",
    val authType: AntigravityAuthType = AntigravityAuthType.NONE,
    val hasApiKey: Boolean = false
)

data class DeviceAuthCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int
)

@Singleton
open class AntigravityAuthManager(
    @ApplicationContext private val context: Context? = null,
    private val secretStore: SecretStore? = null
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        secretStore: SecretStore
    ) : this(context as Context?, secretStore as SecretStore?)

    companion object {
        private const val PREFS_NAME = "pocketcli_antigravity_auth"
        private const val KEY_ACCESS_TOKEN = "antigravity_access_token"
        private const val KEY_REFRESH_TOKEN = "antigravity_refresh_token"
        private const val KEY_EXPIRY_MS = "antigravity_expiry_ms"
        private const val KEY_USER_EMAIL = "antigravity_user_email"
        private const val KEY_API_KEY = "antigravity_api_key"
        private const val KEY_SELECTED_MODEL = "antigravity_selected_model"

        // Default Google OAuth Client ID for Antigravity / Gemini CLI
        // Users can also supply custom credentials if preferred
        const val DEFAULT_CLIENT_ID = "764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com"
        const val DEFAULT_SCOPES = "https://www.googleapis.com/auth/generative-language https://www.googleapis.com/auth/userinfo.email openid"

        const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    }

    private val prefs by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val okHttpClient = OkHttpClient.Builder()
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
        val selectedModel = p.getString(KEY_SELECTED_MODEL, "gemini-2.5-pro") ?: "gemini-2.5-pro"
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
     * Handle OAuth code from deep link redirect (pocketcli://auth?code=...).
     */
    open suspend fun exchangeAuthCode(
        code: String,
        redirectUri: String = "pocketcli://auth",
        codeVerifier: String? = null,
        clientId: String = DEFAULT_CLIENT_ID
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = FormBody.Builder()
                .add("client_id", clientId)
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
     * Obtains a valid Bearer token for Google APIs, refreshing it automatically if expired.
     */
    open suspend fun getValidAccessToken(clientId: String = DEFAULT_CLIENT_ID): Result<String> = withContext(Dispatchers.IO) {
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
            val refreshResult = refreshAccessToken(refreshToken, clientId)
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

    private suspend fun refreshAccessToken(refreshToken: String, clientId: String): Result<String> {
        return runCatching {
            val body = FormBody.Builder()
                .add("client_id", clientId)
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
