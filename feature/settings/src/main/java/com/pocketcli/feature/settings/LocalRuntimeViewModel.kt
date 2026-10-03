package com.pocketcli.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.security.ProviderKeyStore
import com.pocketcli.data.local.db.ProfileDao
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import com.pocketcli.runtime.local.installer.InstallerState
import com.pocketcli.runtime.local.installer.RuntimeInstaller
import com.pocketcli.runtime.local.supervisor.LocalRuntimeState
import com.pocketcli.runtime.local.supervisor.LocalRuntimeSupervisor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalRuntimeUiState(
    val showLogsDialog: Boolean = false,
    val showProviderKeysDialog: Boolean = false,
    val showOnboardingDialog: Boolean = false,
    val logFilterQuery: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class LocalRuntimeViewModel @Inject constructor(
    private val runtimeInstaller: RuntimeInstaller,
    private val supervisor: LocalRuntimeSupervisor,
    private val providerKeyStore: ProviderKeyStore,
    private val profileDao: ProfileDao,
    private val connectionManager: ActiveConnectionManager
) : ViewModel() {

    val installerState: StateFlow<InstallerState> = runtimeInstaller.state
    val supervisorState: StateFlow<LocalRuntimeState> = supervisor.state
    val logs: StateFlow<List<String>> = supervisor.logBuffer.linesFlow

    val providerKeys: StateFlow<Map<String, String>> = providerKeyStore.keysFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _uiState = MutableStateFlow(LocalRuntimeUiState())
    val uiState: StateFlow<LocalRuntimeUiState> = _uiState.asStateFlow()

    init {
        checkOnboardingStatus()
    }

    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val profiles = profileDao.getAllList()
            if (profiles.isEmpty() && !runtimeInstaller.isInstalled()) {
                _uiState.update { it.copy(showOnboardingDialog = true) }
            }
        }
    }

    fun dismissOnboarding() {
        _uiState.update { it.copy(showOnboardingDialog = false) }
    }

    fun openLogsDialog() {
        _uiState.update { it.copy(showLogsDialog = true) }
    }

    fun dismissLogsDialog() {
        _uiState.update { it.copy(showLogsDialog = false) }
    }

    fun openProviderKeysDialog() {
        _uiState.update { it.copy(showProviderKeysDialog = true) }
    }

    fun dismissProviderKeysDialog() {
        _uiState.update { it.copy(showProviderKeysDialog = false) }
    }

    fun updateLogFilter(query: String) {
        _uiState.update { it.copy(logFilterQuery = query) }
    }

    fun clearLogs() {
        supervisor.logBuffer.clear()
    }

    fun install() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = runtimeInstaller.install()
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun uninstall() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            if (supervisor.state.value is LocalRuntimeState.Running) {
                supervisor.stopServer()
            }
            val result = runtimeInstaller.uninstall()
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun startServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = supervisor.startServer(autoRestart = true)
            if (result.isSuccess) {
                // Activate local profile
                val profile = profileDao.getById(LocalRuntimeSupervisor.LOCAL_PROFILE_ID)
                if (profile != null) {
                    connectionManager.setActiveProfile(profile)
                }
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            supervisor.stopServer()
        }
    }

    fun restartServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            val result = supervisor.restartServer()
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun saveProviderKey(envVarName: String, rawKey: String) {
        viewModelScope.launch {
            providerKeyStore.setKey(envVarName, rawKey)
        }
    }

    fun removeProviderKey(envVarName: String) {
        viewModelScope.launch {
            providerKeyStore.removeKey(envVarName)
        }
    }

    fun activateLocalProfile() {
        viewModelScope.launch {
            val profile = profileDao.getById(LocalRuntimeSupervisor.LOCAL_PROFILE_ID)
            if (profile != null) {
                connectionManager.setActiveProfile(profile)
            }
        }
    }
}
