package com.pocketcli.feature.settings

import com.pocketcli.core.model.ConnectionProfile
import com.pocketcli.core.security.ProviderKeyStore
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.local.db.ConnectionProfileEntity
import com.pocketcli.data.local.db.ProfileDao
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import com.pocketcli.runtime.local.installer.InstallState
import com.pocketcli.runtime.local.manifest.ManifestParser
import com.pocketcli.runtime.local.installer.RuntimeInstaller
import com.pocketcli.runtime.local.proot.ProotEnvironment
import com.pocketcli.runtime.local.supervisor.CircularLogBuffer
import com.pocketcli.runtime.local.supervisor.LocalRuntimeState
import com.pocketcli.runtime.local.supervisor.LocalRuntimeSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LocalRuntimeViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var filesDir: File
    private lateinit var fakeInstaller: FakeRuntimeInstaller
    private lateinit var fakeSupervisor: FakeLocalRuntimeSupervisor
    private lateinit var fakeKeyStore: FakeProviderKeyStore
    private lateinit var fakeProfileDao: FakeProfileDao
    private lateinit var fakeSecretStore: FakeSecretStore
    private lateinit var connectionManager: ActiveConnectionManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        filesDir = tempFolder.newFolder("filesDir")

        val prootEnv = ProotEnvironment(
            nativeLibDirProvider = { filesDir },
            filesDirProvider = { filesDir }
        )

        fakeInstaller = FakeRuntimeInstaller(prootEnv, filesDir)
        fakeKeyStore = FakeProviderKeyStore()
        fakeProfileDao = FakeProfileDao()
        fakeSecretStore = FakeSecretStore()

        fakeSupervisor = FakeLocalRuntimeSupervisor(
            prootEnv = prootEnv,
            installer = fakeInstaller,
            profileDao = fakeProfileDao,
            secretStore = fakeSecretStore,
            filesDir = filesDir
        )

        connectionManager = ActiveConnectionManager(
            profileDao = fakeProfileDao,
            secretStore = fakeSecretStore,
            coroutineScope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOnboardingShownWhenNoProfilesAndNotInstalled() = runTest(testDispatcher) {
        fakeProfileDao.profilesList = emptyList()
        fakeInstaller.fakeInstalled = false

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showOnboardingDialog)

        viewModel.dismissOnboarding()
        assertFalse(viewModel.uiState.value.showOnboardingDialog)

        viewModel.dismissOnboardingDialog()
        assertFalse(viewModel.uiState.value.showOnboardingDialog)
    }

    @Test
    fun testOnboardingNotShownWhenAlreadyInstalled() = runTest(testDispatcher) {
        fakeProfileDao.profilesList = emptyList()
        fakeInstaller.fakeInstalled = true

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showOnboardingDialog)
    }

    @Test
    fun testInstallDelegatesToRuntimeInstaller() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.install()
        testScheduler.advanceUntilIdle()

        assertTrue(fakeInstaller.installCalled)
    }

    @Test
    fun testStartServerActivatesLocalProfile() = runTest(testDispatcher) {
        val localProfile = ConnectionProfileEntity(
            id = LocalRuntimeSupervisor.LOCAL_PROFILE_ID,
            name = "Локальный агент",
            url = "http://127.0.0.1:4096"
        )
        fakeProfileDao.upsert(localProfile)

        val viewModel = createViewModel()
        viewModel.startServer()
        testScheduler.advanceUntilIdle()

        assertTrue(fakeSupervisor.startCalled)
        assertEquals(LocalRuntimeSupervisor.LOCAL_PROFILE_ID, connectionManager.activeProfile.value?.id)
    }

    @Test
    fun testStopAndRestartServer() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.stopServer()
        testScheduler.advanceUntilIdle()
        assertTrue(fakeSupervisor.stopCalled)

        viewModel.restartServer()
        testScheduler.advanceUntilIdle()
        assertTrue(fakeSupervisor.restartCalled)
    }

    @Test
    fun testProviderKeysOperations() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.saveProviderKey("ANTHROPIC_API_KEY", "sk-ant-test")
        testScheduler.advanceUntilIdle()
        assertEquals("sk-ant-test", fakeKeyStore.map["ANTHROPIC_API_KEY"])

        viewModel.removeProviderKey("ANTHROPIC_API_KEY")
        testScheduler.advanceUntilIdle()
        assertNull(fakeKeyStore.map["ANTHROPIC_API_KEY"])
    }

    @Test
    fun testDialogVisibilityAndLogFiltering() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.openLogsDialog()
        assertTrue(viewModel.uiState.value.showLogsDialog)
        viewModel.dismissLogsDialog()
        assertFalse(viewModel.uiState.value.showLogsDialog)

        viewModel.openProviderKeysDialog()
        assertTrue(viewModel.uiState.value.showProviderKeysDialog)
        viewModel.dismissProviderKeysDialog()
        assertFalse(viewModel.uiState.value.showProviderKeysDialog)

        viewModel.updateLogFilter("error")
        assertEquals("error", viewModel.uiState.value.logFilterQuery)
    }

    private fun createViewModel(): LocalRuntimeViewModel {
        return LocalRuntimeViewModel(
            runtimeInstaller = fakeInstaller,
            supervisor = fakeSupervisor,
            providerKeyStore = fakeKeyStore,
            profileDao = fakeProfileDao,
            connectionManager = connectionManager
        )
    }

    private class FakeProviderKeyStore : ProviderKeyStore {
        val map = mutableMapOf<String, String>()
        private val _flow = MutableStateFlow<Map<String, String>>(emptyMap())
        override val keysFlow: Flow<Map<String, String>> = _flow.asStateFlow()

        override suspend fun getDecryptedKey(envVarName: String): String? = map[envVarName]

        override suspend fun setKey(envVarName: String, rawKey: String) {
            map[envVarName] = rawKey
            _flow.value = map.toMap()
        }

        override suspend fun removeKey(envVarName: String) {
            map.remove(envVarName)
            _flow.value = map.toMap()
        }

        override suspend fun getAllDecrypted(): Map<String, String> = map.toMap()
    }

    private class FakeSecretStore : SecretStore {
        override fun encrypt(plaintext: String): String = "enc_$plaintext"
        override fun decrypt(encryptedPayload: String): String = encryptedPayload.removePrefix("enc_")
    }

    private class FakeProfileDao : ProfileDao {
        var profilesList = listOf<ConnectionProfileEntity>()
        private val map = mutableMapOf<String, ConnectionProfileEntity>()

        override suspend fun upsert(profile: ConnectionProfileEntity) {
            map[profile.id] = profile
        }

        override suspend fun update(profile: ConnectionProfileEntity) = upsert(profile)
        override fun getAll(): Flow<List<ConnectionProfileEntity>> = MutableStateFlow(profilesList)
        override suspend fun getAllList(): List<ConnectionProfileEntity> = profilesList.ifEmpty { map.values.toList() }
        override suspend fun getById(id: String): ConnectionProfileEntity? = map[id]
        override suspend fun deleteById(id: String) { map.remove(id) }
    }

    private class FakeRuntimeInstaller(
        prootEnv: ProotEnvironment,
        filesDir: File
    ) : RuntimeInstaller(
        prootEnvironment = prootEnv,
        manifestParser = ManifestParser(),
        okHttpClient = OkHttpClient(),
        filesDirProvider = { filesDir },
        manifestContentProvider = { "{}" }
    ) {
        var fakeInstalled = false
        var installCalled = false
        var uninstallCalled = false
        val stateFlow = MutableStateFlow<InstallState>(InstallState.NotInstalled)

        override val state = stateFlow.asStateFlow()
        override fun isInstalled(): Boolean = fakeInstalled

        override suspend fun install(force: Boolean): Result<Unit> {
            installCalled = true
            fakeInstalled = true
            stateFlow.value = InstallState.Ready("1.2.27")
            return Result.success(Unit)
        }

        override suspend fun uninstall(deleteCachedDownloads: Boolean): Result<Unit> {
            uninstallCalled = true
            fakeInstalled = false
            stateFlow.value = InstallState.NotInstalled
            return Result.success(Unit)
        }
    }

    private class FakeLocalRuntimeSupervisor(
        prootEnv: ProotEnvironment,
        installer: RuntimeInstaller,
        profileDao: ProfileDao,
        secretStore: SecretStore,
        filesDir: File
    ) : LocalRuntimeSupervisor(
        prootEnvironment = prootEnv,
        runtimeInstaller = installer,
        profileDao = profileDao,
        secretStore = secretStore,
        logBuffer = CircularLogBuffer(capacity = 50),
        workspacesDirProvider = { File(filesDir, "workspaces") }
    ) {
        var startCalled = false
        var stopCalled = false
        var restartCalled = false
        val stateFlow = MutableStateFlow<LocalRuntimeState>(LocalRuntimeState.Stopped)

        override val state = stateFlow.asStateFlow()

        override suspend fun startServer(autoRestart: Boolean): Result<Unit> {
            startCalled = true
            stateFlow.value = LocalRuntimeState.Running(4096, "token", 1234, System.currentTimeMillis())
            return Result.success(Unit)
        }

        override suspend fun stopServer(): Result<Unit> {
            stopCalled = true
            stateFlow.value = LocalRuntimeState.Stopped
            return Result.success(Unit)
        }

        override suspend fun restartServer(): Result<Unit> {
            restartCalled = true
            stateFlow.value = LocalRuntimeState.Running(4096, "token", 1234, System.currentTimeMillis())
            return Result.success(Unit)
        }
    }
}
