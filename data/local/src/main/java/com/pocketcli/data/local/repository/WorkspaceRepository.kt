package com.pocketcli.data.local.repository

import com.pocketcli.core.model.Workspace
import com.pocketcli.core.model.WorkspaceSourceType
import com.pocketcli.data.local.db.WorkspaceDao
import com.pocketcli.data.local.db.WorkspaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepository @Inject constructor(
    private val workspaceDao: WorkspaceDao
) {

    fun getWorkspaces(profileId: String): Flow<List<Workspace>> {
        return workspaceDao.getWorkspaces(profileId).map { list ->
            list.map { it.toDomain() }
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

    suspend fun saveWorkspace(workspace: Workspace) {
        workspaceDao.upsert(workspace.toEntity())
    }

    suspend fun touchWorkspace(id: String) {
        workspaceDao.updateLastOpened(id)
    }

    suspend fun deleteWorkspace(id: String) {
        workspaceDao.deleteById(id)
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
