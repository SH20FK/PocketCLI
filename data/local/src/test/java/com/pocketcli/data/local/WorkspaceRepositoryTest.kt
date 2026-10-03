package com.pocketcli.data.local

import app.cash.turbine.test
import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.data.local.db.WorkspaceDao
import com.pocketcli.data.local.db.WorkspaceEntity
import com.pocketcli.data.local.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorkspaceRepositoryTest {

    private lateinit var fakeDao: FakeWorkspaceDao
    private lateinit var repository: WorkspaceRepository

    @Before
    fun setup() {
        fakeDao = FakeWorkspaceDao()
        repository = WorkspaceRepository(fakeDao)
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
            // Initial empty
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
}
