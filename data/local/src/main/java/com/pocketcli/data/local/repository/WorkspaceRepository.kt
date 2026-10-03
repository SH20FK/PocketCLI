package com.pocketcli.data.local.repository

import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceGitStatus
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.core.model.WorkspaceWithDetails
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.local.db.SessionDao
import com.pocketcli.data.local.db.WorkspaceDao
import com.pocketcli.data.local.db.WorkspaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepository @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val sessionDao: SessionDao,
    private val storage: WorkspaceStorage,
    private val secretStore: SecretStore
) {

    fun getWorkspaces(profileId: String): Flow<List<Workspace>> {
        return workspaceDao.getWorkspaces(profileId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getWorkspacesWithDetails(profileId: String): Flow<List<WorkspaceWithDetails>> {
        return workspaceDao.getWorkspaces(profileId).map { list ->
            withContext(Dispatchers.IO) {
                list.map { entity ->
                    val domain = entity.toDomain()
                    val dir = storage.getWorkspaceDirectory(domain.id)
                    val gitStatus = storage.getGitStatus(dir)
                    val sessionCount = sessionDao.countSessionsForWorkspace(domain.id)
                    WorkspaceWithDetails(
                        workspace = domain,
                        gitStatus = gitStatus,
                        sessionCount = sessionCount
                    )
                }
            }
        }
    }

    fun getArchivedWorkspaces(profileId: String): Flow<List<Workspace>> {
        return workspaceDao.getArchivedWorkspaces(profileId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getWorkspace(id: String): Workspace? {
        return workspaceDao.getById(id)?.toDomain()
    }

    suspend fun getWorkspaceWithDetails(id: String): WorkspaceWithDetails? = withContext(Dispatchers.IO) {
        val entity = workspaceDao.getById(id) ?: return@withContext null
        val domain = entity.toDomain()
        val dir = storage.getWorkspaceDirectory(domain.id)
        val gitStatus = storage.getGitStatus(dir)
        val sessionCount = sessionDao.countSessionsForWorkspace(domain.id)
        WorkspaceWithDetails(
            workspace = domain,
            gitStatus = gitStatus,
            sessionCount = sessionCount
        )
    }

    suspend fun createWorkspace(
        profileId: String,
        displayName: String,
        sourceType: WorkspaceSourceType = WorkspaceSourceType.CREATED,
        remoteUrl: String? = null,
        defaultBranch: String = "main",
        token: String? = null,
        initReadme: Boolean = true
    ): Workspace = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dir = storage.createWorkspaceDirectory(id, initReadme = initReadme, title = displayName)

        if (!token.isNullOrBlank()) {
            val encryptedToken = secretStore.encrypt(token)
            val tokenFile = File(dir, ".pocketcli_token")
            try {
                tokenFile.writeText(encryptedToken)
            } catch (_: Exception) {}
        }

        val workspace = Workspace(
            id = id,
            profileId = profileId,
            displayName = displayName,
            localPath = dir.absolutePath,
            sourceType = sourceType,
            remoteUrl = remoteUrl,
            defaultBranch = defaultBranch,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            archived = false
        )
        workspaceDao.upsert(workspace.toEntity())
        workspace
    }

    suspend fun saveWorkspace(workspace: Workspace) {
        workspaceDao.upsert(workspace.toEntity())
    }

    suspend fun touchWorkspace(id: String) {
        workspaceDao.updateLastOpened(id)
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        workspaceDao.setArchived(id, archived)
    }

    suspend fun deleteWorkspace(id: String, deleteFiles: Boolean = true) = withContext(Dispatchers.IO) {
        workspaceDao.deleteById(id)
        if (deleteFiles) {
            storage.deleteWorkspaceDirectory(id)
        }
    }

    fun getWorkspaceDirectory(id: String): File {
        return storage.getWorkspaceDirectory(id)
    }

    suspend fun getWorkspaceToken(workspaceId: String): String? = withContext(Dispatchers.IO) {
        val dir = storage.getWorkspaceDirectory(workspaceId)
        val tokenFile = File(dir, ".pocketcli_token")
        if (tokenFile.exists()) {
            try {
                val encrypted = tokenFile.readText().trim()
                if (encrypted.isNotEmpty()) {
                    return@withContext secretStore.decrypt(encrypted)
                }
            } catch (_: Exception) {}
        }
        null
    }

    private fun WorkspaceEntity.toDomain(): Workspace {
        val type = try {
            WorkspaceSourceType.valueOf(sourceType)
        } catch (_: Exception) {
            WorkspaceSourceType.CREATED
        }
        return Workspace(
            id = id,
            profileId = profileId,
            displayName = displayName,
            localPath = localPath,
            sourceType = type,
            remoteUrl = remoteUrl,
            defaultBranch = defaultBranch,
            createdAt = createdAt,
            lastOpenedAt = lastOpenedAt,
            archived = archived
        )
    }

    private fun Workspace.toEntity(): WorkspaceEntity {
        return WorkspaceEntity(
            id = id,
            profileId = profileId,
            displayName = displayName,
            localPath = localPath,
            sourceType = sourceType.name,
            remoteUrl = remoteUrl,
            defaultBranch = defaultBranch,
            createdAt = createdAt,
            lastOpenedAt = lastOpenedAt,
            archived = archived
        )
    }
}
