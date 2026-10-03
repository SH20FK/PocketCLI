package com.pocketcli

import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.api.BasicAuthInterceptor
import com.pocketcli.data.opencode.api.CleartextHttpPolicyInterceptor
import com.pocketcli.data.opencode.api.OpenCodeApiClient
import com.pocketcli.data.opencode.db.AppDatabase
import com.pocketcli.data.opencode.db.ConnectionProfileEntity
import com.pocketcli.data.opencode.sse.OpenCodeSseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveConnectionManager @Inject constructor(
    private val database: AppDatabase,
    private val secretStore: SecretStore
) {
    private val _activeProfile = MutableStateFlow<ConnectionProfileEntity?>(null)
    val activeProfile: StateFlow<ConnectionProfileEntity?> = _activeProfile.asStateFlow()

    private var cachedAdapter: OpenCodeAdapter? = null

    fun setActiveProfile(profile: ConnectionProfileEntity) {
        _activeProfile.value = profile
        cachedAdapter = null
    }

    fun getAdapter(): OpenCodeAdapter {
        cachedAdapter?.let { return it }

        val profile = _activeProfile.value ?: ConnectionProfileEntity(
            id = "default_local",
            name = "Default Local",
            url = "http://10.0.2.2:4096",
            username = "opencode",
            allowCleartextHttp = true
        )

        val decryptedPassword = if (profile.encryptedPassword.isNotEmpty()) {
            secretStore.decrypt(profile.encryptedPassword)
        } else ""

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(CleartextHttpPolicyInterceptor { profile.allowCleartextHttp })
            .addInterceptor(BasicAuthInterceptor(
                usernameProvider = { profile.username },
                passwordProvider = { decryptedPassword }
            ))
            .build()

        val apiClient = OpenCodeApiClient(
            okHttpClient = okHttpClient,
            baseUrlProvider = { profile.url }
        )

        val sseClient = OpenCodeSseClient(
            baseOkHttpClient = okHttpClient,
            baseUrlProvider = { profile.url }
        )

        val adapter = OpenCodeAdapter(
            apiClient = apiClient,
            sseClient = sseClient,
            profileId = profile.id
        )
        cachedAdapter = adapter
        return adapter
    }
}
