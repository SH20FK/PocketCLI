package com.pocketcli.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.opencode.api.CleartextHttpPolicyInterceptor
import com.pocketcli.data.opencode.api.OpenCodeApiClient
import com.pocketcli.data.opencode.db.AppDatabase
import com.pocketcli.data.opencode.db.ConnectionProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val profiles: List<ConnectionProfileEntity> = emptyList(),
    val activeProfileId: String = "",
    val showAddDialog: Boolean = false,
    val draftName: String = "",
    val draftUrl: String = "http://10.0.2.2:4096",
    val draftUsername: String = "opencode",
    val draftPassword: String = "",
    val draftAllowCleartext: Boolean = false,
    val testConnectionStatus: String? = null,
    val isTestingConnection: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: AppDatabase,
    private val secretStore: SecretStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            database.profileDao().getAll().collect { list ->
                _uiState.update { current ->
                    val activeId = if (current.activeProfileId.isEmpty() && list.isNotEmpty()) {
                        list.first().id
                    } else {
                        current.activeProfileId
                    }
                    current.copy(profiles = list, activeProfileId = activeId)
                }
            }
        }
    }

    fun selectActiveProfile(id: String) {
        _uiState.update { it.copy(activeProfileId = id) }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                draftName = "My OpenCode",
                draftUrl = "http://10.0.2.2:4096",
                draftUsername = "opencode",
                draftPassword = "",
                draftAllowCleartext = false,
                testConnectionStatus = null
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun onDraftNameChange(name: String) = _uiState.update { it.copy(draftName = name) }
    fun onDraftUrlChange(url: String) = _uiState.update { it.copy(draftUrl = url) }
    fun onDraftUsernameChange(user: String) = _uiState.update { it.copy(draftUsername = user) }
    fun onDraftPasswordChange(pass: String) = _uiState.update { it.copy(draftPassword = pass) }
    fun onDraftCleartextToggle(allow: Boolean) = _uiState.update { it.copy(draftAllowCleartext = allow) }

    fun testConnection() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, testConnectionStatus = null) }
            val client = OkHttpClient.Builder()
                .addInterceptor(CleartextHttpPolicyInterceptor { state.draftAllowCleartext })
                .build()
            val apiClient = OpenCodeApiClient(
                okHttpClient = client,
                baseUrlProvider = { state.draftUrl }
            )

            val result = apiClient.getHealth()
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    testConnectionStatus = result.fold(
                        onSuccess = { health -> "Success! OpenCode v${health.version} connected." },
                        onFailure = { err -> "Connection failed: ${err.message}" }
                    )
                )
            }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        val encryptedPass = secretStore.encrypt(state.draftPassword)
        val profile = ConnectionProfileEntity(
            id = UUID.randomUUID().toString(),
            name = state.draftName.ifBlank { "OpenCode Server" },
            url = state.draftUrl.trimEnd('/'),
            username = state.draftUsername,
            encryptedPassword = encryptedPass,
            allowCleartextHttp = state.draftAllowCleartext,
            lastConnectedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            database.profileDao().upsert(profile)
            _uiState.update { it.copy(showAddDialog = false, activeProfileId = profile.id) }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            database.profileDao().deleteById(id)
        }
    }
}
