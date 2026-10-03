package com.pocketcli.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface ProviderKeyStore {
    val keysFlow: Flow<Map<String, String>>
    suspend fun getDecryptedKey(envVarName: String): String?
    suspend fun setKey(envVarName: String, rawKey: String)
    suspend fun removeKey(envVarName: String)
    suspend fun getAllDecrypted(): Map<String, String>
}

@Singleton
class SharedPreferencesProviderKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: SecretStore
) : ProviderKeyStore {

    companion object {
        private const val PREFS_NAME = "pocketcli_provider_keys"
        val KNOWN_PROVIDERS = listOf(
            "ANTHROPIC_API_KEY" to "Anthropic (Claude)",
            "OPENAI_API_KEY" to "OpenAI (GPT / Codex)",
            "GEMINI_API_KEY" to "Google Gemini",
            "DEEPSEEK_API_KEY" to "DeepSeek",
            "OPENROUTER_API_KEY" to "OpenRouter",
            "GROQ_API_KEY" to "Groq"
        )
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _keysFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    override val keysFlow: Flow<Map<String, String>> = _keysFlow.asStateFlow()

    init {
        loadKeysIntoFlow()
    }

    private fun loadKeysIntoFlow() {
        val map = mutableMapOf<String, String>()
        for ((key, _) in KNOWN_PROVIDERS) {
            val enc = prefs.getString(key, null)
            if (!enc.isNullOrEmpty()) {
                val decrypted = secretStore.decrypt(enc)
                if (decrypted.isNotEmpty()) {
                    map[key] = decrypted
                }
            }
        }
        _keysFlow.value = map
    }

    override suspend fun getDecryptedKey(envVarName: String): String? = withContext(Dispatchers.IO) {
        val enc = prefs.getString(envVarName, null) ?: return@withContext null
        secretStore.decrypt(enc).ifEmpty { null }
    }

    override suspend fun setKey(envVarName: String, rawKey: String): Unit = withContext(Dispatchers.IO) {
        if (rawKey.isBlank()) {
            removeKey(envVarName)
            return@withContext
        }
        val encrypted = secretStore.encrypt(rawKey.trim())
        prefs.edit().putString(envVarName, encrypted).apply()
        loadKeysIntoFlow()
    }

    override suspend fun removeKey(envVarName: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(envVarName).apply()
        loadKeysIntoFlow()
    }

    override suspend fun getAllDecrypted(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        for ((key, _) in KNOWN_PROVIDERS) {
            val enc = prefs.getString(key, null)
            if (!enc.isNullOrEmpty()) {
                val dec = secretStore.decrypt(enc)
                if (dec.isNotEmpty()) {
                    result[key] = dec
                }
            }
        }
        result
    }
}
