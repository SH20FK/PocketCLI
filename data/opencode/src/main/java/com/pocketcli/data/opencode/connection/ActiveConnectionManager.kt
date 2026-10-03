package com.pocketcli.data.opencode.connection

import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.api.BasicAuthInterceptor
import com.pocketcli.data.opencode.api.CleartextHttpPolicyInterceptor
import com.pocketcli.data.opencode.api.OpenCodeApiClient
import com.pocketcli.data.opencode.db.ConnectionProfileEntity
import com.pocketcli.data.opencode.db.ProfileDao
import com.pocketcli.data.opencode.sse.OpenCodeSseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveConnectionManager @Inject constructor(
    private val profileDao: ProfileDao,
    private val secretStore: SecretStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _activeProfile = MutableStateFlow<ConnectionProfileEntity?>(null)
    val activeProfile: StateFlow<ConnectionProfileEntity?> = _activeProfile.asStateFlow()

    private var cachedAdapter: OpenCodeAdapter? = null

    init {
        scope.launch {
            profileDao.getAll().collectLatest { profiles ->
                if (_activeProfile.value == null && profiles.isNotEmpty()) {
                    _activeProfile.value = profiles.first()
                    cachedAdapter = null
                } else if (_activeProfile.value != null) {
                    val matching = profiles.find { it.id == _activeProfile.value?.id }
                    if (matching != null) {
                        _activeProfile.value = matching
                    } else if (profiles.isNotEmpty()) {
                        _activeProfile.value = profiles.first()
                        cachedAdapter = null
                    } else {
                        _activeProfile.value = null
                        cachedAdapter = null
                    }
                }
            }
        }
    }

    fun setActiveProfile(profile: ConnectionProfileEntity) {
        _activeProfile.value = profile
        cachedAdapter = null
        scope.launch {
            profileDao.update(profile.copy(lastConnectedAt = System.currentTimeMillis()))
        }
    }

    fun getActiveProfileId(): String? = _activeProfile.value?.id ?: runBlocking(Dispatchers.IO) {
        profileDao.getAllList().firstOrNull()?.id
    }

    @Synchronized
    fun getAdapter(): OpenCodeAdapter? {
        cachedAdapter?.let { return it }

        var profile = _activeProfile.value
        if (profile == null) {
            profile = runBlocking(Dispatchers.IO) {
                profileDao.getAllList().firstOrNull()
            }
            if (profile != null) {
                _activeProfile.value = profile
            }
        }

        if (profile == null) {
            return null
        }

        val decryptedPassword = if (profile.encryptedPassword.isNotEmpty()) {
            secretStore.decrypt(profile.encryptedPassword)
        } else ""

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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
