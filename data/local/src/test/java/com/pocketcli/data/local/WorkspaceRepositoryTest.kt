package com.pocketcli.data.local

import app.cash.turbine.test
import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceGitStatus
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.local.db.SessionDao
import com.pocketcli.data.local.db.SessionEntity
import com.pocketcli.data.local.db.WorkspaceDao
import com.pocketcli.data.local.db.WorkspaceEntity
import com.pocketcli.data.local.repository.WorkspaceRepository
import com.pocketcli.data.local.repository.WorkspaceStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class WorkspaceRepositoryTest {

    private lateinit var fakeWorkspaceDao: FakeWorkspaceDao
    private lateinit var fakeSessionDao: FakeSessionDao
    private lateinit var fakeStorage: FakeWorkspaceStorage
    private lateinit var fakeSecretStore: FakeSecretStore
    private lateinit var repository: WorkspaceRepository

    @Before
    fun setup() {
        fakeWorkspaceDao = FakeWorkspaceDao()
        fakeSessionDao = FakeSessionDao()
        fakeStorage = FakeWorkspaceStorage()
        fakeSecretStore = FakeSecretStore()
        repository = WorkspaceRepository(
            workspaceDao = fakeWorkspaceDao,
            sessionDao = fakeSessionDao,
            storage = fakeStorage,
            secretStore = fakeSecretStore
        )
    }

    @Test
    fun testSaveAndGetWorkspace() = runBlocking {
        val workspace = Workspace(
            id = "ws_123",
            profileId = "profile_1",
            displayName = "My Repo",
            localPath = "/workspace/ws_123",
            sourceType = WorkspaceSourceType.CLONED,
            remoteUrl = "https://github.com/test/repo.git",
            defaultBranch = "main"
        )

        repository.saveWorkspace(workspace)

        val fetched = repository.getWorkspace("ws_123")
        assertNotNull(fetched)
        assertEquals("ws_123", fetched?.id)
        assertEquals("profile_1", fetched?.profileId)
        assertEquals("My Repo", fetched?.displayName)
        assertEquals(WorkspaceSourceType.CLONED, fetched?.sourceType)
        assertEquals("https://github.com/test/repo.git", fetched?.remoteUrl)
    }

    @Test
    fun testGetWorkspacesFlow() = runBlocking {
        repository.getWorkspaces("profile_1").test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())

            repository.saveWorkspace(
                Workspace(
                    id = "ws_1",
                    profileId = "profile_1",
                    displayName = "Project One",
                    localPath = "/workspace/ws_1"
                )
            )

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Project One", updated[0].displayName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testCreateWorkspaceWithToken() = runBlocking {
        val created = repository.createWorkspace(
            profileId = "prof_local",
            displayName = "New Repo",
            sourceType = WorkspaceSourceType.CLONED,
            remoteUrl = "https://github.com/org/repo.git",
            defaultBranch = "main",
            token = "ghp_secret_token_123"
        )

        assertNotNull(created)
        assertEquals("New Repo", created.displayName)
        assertEquals("prof_local", created.profileId)

        val fetched = repository.getWorkspace(created.id)
        assertNotNull(fetched)

        val decrypted = repository.getWorkspaceToken(created.id)
        assertEquals("ghp_secret_token_123", decrypted)
    }

    @Test
    fun testSetArchived() = runBlocking {
        val created = repository.createWorkspace(
            profileId = "prof_1",
            displayName = "Archivable"
        )

        repository.setArchived(created.id, true)
        val fetched = repository.getWorkspace(created.id)
        assertTrue(fetched?.archived == true)
    }

    @Test
    fun testDeleteWorkspace() = runBlocking {
        val created = repository.createWorkspace(
            profileId = "prof_1",
            displayName = "To Delete"
        )

        assertNotNull(repository.getWorkspace(created.id))
        repository.deleteWorkspace(created.id, deleteFiles = true)
        assertNull(repository.getWorkspace(created.id))
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

        override suspend fun getById(id: String): WorkspaceEntity? {
            return storage[id]
        }

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
        private val sessions = mutableListOf<SessionEntity>()

        override suspend fun upsert(session: SessionEntity) {
            sessions.removeAll { it.sessionId == session.sessionId }
            sessions.add(session)
        }

        override suspend fun upsertAll(sessions: List<SessionEntity>) {
            for (s in sessions) upsert(s)
        }

        override fun getSessions(profileId: String): Flow<List<SessionEntity>> {
            return MutableStateFlow(sessions.filter { it.profileId == profileId })
        }

        override suspend fun getSession(profileId: String, sessionId: String): SessionEntity? {
            return sessions.find { it.profileId == profileId && it.sessionId == sessionId }
        }

        override suspend fun getSessionBySessionId(sessionId: String): SessionEntity? {
            return sessions.find { it.sessionId == sessionId }
        }

        override fun getSessionsForWorkspace(workspaceId: String): Flow<List<SessionEntity>> {
            return MutableStateFlow(sessions.filter { it.workspaceId == workspaceId })
        }

        override fun countSessionsForWorkspaceFlow(workspaceId: String): Flow<Int> {
            return MutableStateFlow(sessions.count { it.workspaceId == workspaceId })
        }

        override suspend fun countSessionsForWorkspace(workspaceId: String): Int {
            return sessions.count { it.workspaceId == workspaceId }
        }

        override suspend fun delete(profileId: String, sessionId: String) {
            sessions.removeAll { it.profileId == profileId && it.sessionId == sessionId }
        }

        override suspend fun deleteByProfile(profileId: String) {
            sessions.removeAll { it.profileId == profileId }
        }
    }

    private class FakeWorkspaceStorage : WorkspaceStorage {
        val tempDir = createTempDir("test_ws_storage")

        override fun getBaseDirectory(): File = tempDir

        override fun getWorkspaceDirectory(workspaceId: String): File {
            val dir = File(tempDir, workspaceId)
            dir.mkdirs()
            return dir
        }

        override fun createWorkspaceDirectory(workspaceId: String, initReadme: Boolean, title: String): File {
            val dir = getWorkspaceDirectory(workspaceId)
            if (initReadme) {
                File(dir, "README.md").writeText("# $title\n")
            }
            return dir
        }

        override fun deleteWorkspaceDirectory(workspaceId: String): Boolean {
            return File(tempDir, workspaceId).deleteRecursively()
        }

        override fun getGitStatus(directory: File): WorkspaceGitStatus {
            return WorkspaceGitStatus(branch = "main", isGitRepo = true, isDirty = false, fileCount = 2)
        }
    }

    private class FakeSecretStore : SecretStore {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(encryptedPayload: String): String = encryptedPayload.removePrefix("enc:")
    }
}
