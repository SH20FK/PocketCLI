package com.pocketcli.feature.projects

import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceGitStatus
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.local.db.*
import com.pocketcli.data.local.repository.WorkspaceRepository
import com.pocketcli.data.local.repository.WorkspaceStorage
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workspaceDao: FakeWorkspaceDao
    private lateinit var sessionDao: FakeSessionDao
    private lateinit var profileDao: FakeProfileDao
    private lateinit var secretStore: FakeSecretStore
    private lateinit var storage: FakeWorkspaceStorage

    private lateinit var workspaceRepository: WorkspaceRepository
    private lateinit var connectionManager: ActiveConnectionManager
    private lateinit var viewModel: ProjectsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workspaceDao = FakeWorkspaceDao()
        sessionDao = FakeSessionDao()
        profileDao = FakeProfileDao()
        secretStore = FakeSecretStore()
        storage = FakeWorkspaceStorage()

        workspaceRepository = WorkspaceRepository(
            workspaceDao = workspaceDao,
            sessionDao = sessionDao,
            storage = storage,
            secretStore = secretStore
        )

        connectionManager = ActiveConnectionManager(
            profileDao = profileDao,
            secretStore = secretStore
        )

        viewModel = ProjectsViewModel(workspaceRepository, connectionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialEmptyState() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("", state.activeProfileId)
        assertTrue(state.workspaces.isEmpty())
        assertFalse(state.showCloneSheet)
        assertFalse(state.showCreateDialog)
    }

    @Test
    fun testActiveProfileLoadsWorkspaces() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        val profile = ConnectionProfileEntity(
            id = "prof_1",
            name = "Test Server",
            url = "http://127.0.0.1:4096",
            username = "opencode",
            encryptedPassword = "enc:password"
        )
        profileDao.upsert(profile)
        connectionManager.setActiveProfile(profile)
        testScheduler.advanceUntilIdle()

        assertEquals("prof_1", viewModel.uiState.value.activeProfileId)
        assertEquals("Test Server", viewModel.uiState.value.activeProfileName)

        // Create a project in repository
        workspaceRepository.createWorkspace(
            profileId = "prof_1",
            displayName = "Alpha Project"
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.workspaces.size)
        assertEquals("Alpha Project", state.workspaces[0].workspace.displayName)
    }

    @Test
    fun testOpenAndDismissDialogs() = runTest(testDispatcher) {
        viewModel.openCloneSheet()
        assertTrue(viewModel.uiState.value.showCloneSheet)

        viewModel.dismissCloneSheet()
        assertFalse(viewModel.uiState.value.showCloneSheet)

        viewModel.openCreateDialog()
        assertTrue(viewModel.uiState.value.showCreateDialog)

        viewModel.dismissCreateDialog()
        assertFalse(viewModel.uiState.value.showCreateDialog)
    }

    @Test
    fun testCreateProjectThroughViewModel() = runTest(testDispatcher) {
        val profile = ConnectionProfileEntity(
            id = "prof_1",
            name = "Local",
            url = "http://127.0.0.1:4096",
            username = "opencode",
            encryptedPassword = "enc:password"
        )
        profileDao.upsert(profile)
        connectionManager.setActiveProfile(profile)
        testScheduler.advanceUntilIdle()

        viewModel.openCreateDialog()
        var createdResult: Workspace? = null
        viewModel.createWorkspace("My New App", initReadme = true) {
            createdResult = it
        }
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCreateDialog)
        assertNotNull(createdResult)
        assertEquals("My New App", createdResult?.displayName)
        assertEquals(1, viewModel.uiState.value.workspaces.size)
    }

    @Test
    fun testCloneProjectThroughViewModel() = runTest(testDispatcher) {
        val profile = ConnectionProfileEntity(
            id = "prof_1",
            name = "Local",
            url = "http://127.0.0.1:4096",
            username = "opencode",
            encryptedPassword = "enc:password"
        )
        profileDao.upsert(profile)
        connectionManager.setActiveProfile(profile)
        testScheduler.advanceUntilIdle()

        viewModel.openCloneSheet()
        var clonedResult: Workspace? = null
        viewModel.cloneWorkspace(
            remoteUrl = "https://github.com/org/repo.git",
            displayName = "Repo",
            branch = "develop",
            token = "ghp_secret"
        ) {
            clonedResult = it
        }
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showCloneSheet)
        assertNotNull(clonedResult)
        assertEquals("Repo", clonedResult?.displayName)
        assertEquals("https://github.com/org/repo.git", clonedResult?.remoteUrl)
        assertEquals("develop", clonedResult?.defaultBranch)
    }

    private class FakeWorkspaceDao : WorkspaceDao {
        private val storage = mutableMapOf<String, WorkspaceEntity>()
        private val flow = MutableStateFlow<List<WorkspaceEntity>>(emptyList())

        override suspend fun upsert(workspace: WorkspaceEntity) {
            storage[workspace.id] = workspace
            flow.value = storage.values.toList()
        }

        override suspend fun update(workspace: WorkspaceEntity) {
            storage[workspace.id] = workspace
            flow.value = storage.values.toList()
        }

        override fun getWorkspaces(profileId: String): Flow<List<WorkspaceEntity>> {
            return flow.asStateFlow()
        }

        override fun getArchivedWorkspaces(profileId: String): Flow<List<WorkspaceEntity>> {
            return flow.asStateFlow()
        }

        override suspend fun getById(id: String): WorkspaceEntity? = storage[id]

        override suspend fun setArchived(id: String, archived: Boolean) {
            storage[id]?.let {
                storage[id] = it.copy(archived = archived)
                flow.value = storage.values.toList()
            }
        }

        override suspend fun updateLastOpened(id: String, timestamp: Long) {
            storage[id]?.let {
                storage[id] = it.copy(lastOpenedAt = timestamp)
                flow.value = storage.values.toList()
            }
        }

        override suspend fun deleteById(id: String) {
            storage.remove(id)
            flow.value = storage.values.toList()
        }
    }

    private class FakeSessionDao : SessionDao {
        private val list = mutableListOf<SessionEntity>()
        override suspend fun upsert(session: SessionEntity) {
            list.removeAll { it.sessionId == session.sessionId }
            list.add(session)
        }
        override suspend fun upsertAll(sessions: List<SessionEntity>) {
            for (s in sessions) upsert(s)
        }
        override fun getSessions(profileId: String): Flow<List<SessionEntity>> = MutableStateFlow(list)
        override suspend fun getSession(profileId: String, sessionId: String): SessionEntity? =
            list.find { it.profileId == profileId && it.sessionId == sessionId }
        override suspend fun getSessionBySessionId(sessionId: String): SessionEntity? =
            list.find { it.sessionId == sessionId }
        override fun getSessionsForWorkspace(workspaceId: String): Flow<List<SessionEntity>> =
            MutableStateFlow(list.filter { it.workspaceId == workspaceId })
        override fun countSessionsForWorkspaceFlow(workspaceId: String): Flow<Int> =
            MutableStateFlow(list.count { it.workspaceId == workspaceId })
        override suspend fun countSessionsForWorkspace(workspaceId: String): Int =
            list.count { it.workspaceId == workspaceId }
        override suspend fun delete(profileId: String, sessionId: String) {
            list.removeAll { it.profileId == profileId && it.sessionId == sessionId }
        }
        override suspend fun deleteByProfile(profileId: String) {
            list.removeAll { it.profileId == profileId }
        }
    }

    private class FakeProfileDao : ProfileDao {
        private val list = mutableListOf<ConnectionProfileEntity>()
        private val flow = MutableStateFlow<List<ConnectionProfileEntity>>(emptyList())
        override suspend fun upsert(profile: ConnectionProfileEntity) {
            list.removeAll { it.id == profile.id }
            list.add(profile)
            flow.value = list.toList()
        }
        override suspend fun update(profile: ConnectionProfileEntity) = upsert(profile)
        override fun getAll(): Flow<List<ConnectionProfileEntity>> = flow.asStateFlow()
        override suspend fun getAllList(): List<ConnectionProfileEntity> = list.toList()
        override suspend fun getById(id: String): ConnectionProfileEntity? = list.find { it.id == id }
        override suspend fun deleteById(id: String) {
            list.removeAll { it.id == id }
            flow.value = list.toList()
        }
    }

    private class FakeSecretStore : SecretStore {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(encryptedPayload: String): String = encryptedPayload.removePrefix("enc:")
    }

    private class FakeWorkspaceStorage : WorkspaceStorage {
        val dir = createTempDir("test_ws_storage")
        override fun getBaseDirectory(): File = dir
        override fun getWorkspaceDirectory(workspaceId: String): File = File(dir, workspaceId).apply { mkdirs() }
        override fun createWorkspaceDirectory(workspaceId: String, initReadme: Boolean, title: String): File =
            File(dir, workspaceId).apply {
                mkdirs()
                if (initReadme) File(this, "README.md").writeText("# $title")
            }
        override fun deleteWorkspaceDirectory(workspaceId: String): Boolean =
            File(dir, workspaceId).deleteRecursively()
        override fun getGitStatus(directory: File): WorkspaceGitStatus =
            WorkspaceGitStatus(branch = "main", isGitRepo = true, isDirty = false, fileCount = 1)
    }
}
