package com.pocketcli.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ConnectionProfileEntity)

    @Update
    suspend fun update(profile: ConnectionProfileEntity)

    @Query("SELECT * FROM connection_profiles ORDER BY lastConnectedAt DESC")
    fun getAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles ORDER BY lastConnectedAt DESC")
    suspend fun getAllList(): List<ConnectionProfileEntity>

    @Query("SELECT * FROM connection_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConnectionProfileEntity?

    @Query("DELETE FROM connection_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WorkspaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: WorkspaceEntity)

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspaces WHERE profileId = :profileId AND archived = 0 ORDER BY lastOpenedAt DESC")
    fun getWorkspaces(profileId: String): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE profileId = :profileId AND archived = 1 ORDER BY lastOpenedAt DESC")
    fun getArchivedWorkspaces(profileId: String): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkspaceEntity?

    @Query("UPDATE workspaces SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY updatedAt DESC")
    fun getSessions(profileId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND sessionId = :sessionId LIMIT 1")
    suspend fun getSession(profileId: String, sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionBySessionId(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    fun getSessionsForWorkspace(workspaceId: String): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE profileId = :profileId AND sessionId = :sessionId")
    suspend fun delete(profileId: String, sessionId: String)

    @Query("DELETE FROM sessions WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE profileId = :profileId AND sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(profileId: String, sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE profileId = :profileId AND sessionId = :sessionId AND messageId = :messageId LIMIT 1")
    suspend fun getMessage(profileId: String, sessionId: String, messageId: String): MessageEntity?
}

@Dao
interface ToolCallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(toolCall: ToolCallEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(toolCalls: List<ToolCallEntity>)

    @Query("SELECT * FROM tool_calls WHERE profileId = :profileId AND sessionId = :sessionId AND messageId = :messageId")
    fun getToolCalls(profileId: String, sessionId: String, messageId: String): Flow<List<ToolCallEntity>>

    @Query("SELECT * FROM tool_calls WHERE profileId = :profileId AND sessionId = :sessionId")
    fun getToolCallsForSession(profileId: String, sessionId: String): Flow<List<ToolCallEntity>>
}

@Database(
    entities = [
        ConnectionProfileEntity::class,
        WorkspaceEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        ToolCallEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun toolCallDao(): ToolCallDao
}
