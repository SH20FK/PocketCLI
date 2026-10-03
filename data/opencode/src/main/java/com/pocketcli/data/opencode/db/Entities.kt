package com.pocketcli.data.opencode.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.nio.charset.StandardCharsets

@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val username: String = "opencode",
    val encryptedPassword: String = "",
    val allowCleartextHttp: Boolean = false,
    val lastConnectedAt: Long = 0L
)

@Entity(
    tableName = "sessions",
    primaryKeys = ["profileId", "sessionId"],
    foreignKeys = [
        ForeignKey(
            entity = ConnectionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class SessionEntity(
    val profileId: String,
    val sessionId: String,
    val title: String,
    val updatedAt: Long,
    val createdAt: Long
)

@Entity(
    tableName = "messages",
    primaryKeys = ["profileId", "sessionId", "messageId"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["profileId", "sessionId"],
            childColumns = ["profileId", "sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId", "sessionId"])]
)
data class MessageEntity(
    val profileId: String,
    val sessionId: String,
    val messageId: String,
    val role: String,
    val text: String,
    val reasoning: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tool_calls",
    primaryKeys = ["profileId", "sessionId", "messageId", "callId"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["profileId", "sessionId", "messageId"],
            childColumns = ["profileId", "sessionId", "messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId", "sessionId", "messageId"])]
)
data class ToolCallEntity(
    val profileId: String,
    val sessionId: String,
    val messageId: String,
    val callId: String,
    val name: String,
    val status: String,
    val inputJson: String?,
    val output: String?,
    val isTruncated: Boolean = false
)

object DbSanitizer {
    /**
     * Prevents Android SQLite CursorWindow 2MB exception by truncating very large tool outputs.
     * Keeps first 64KB and last 128KB with truncation indicator.
     */
    fun sanitizeOutput(output: String?, maxBytes: Int = 256 * 1024): Pair<String?, Boolean> {
        if (output == null) return null to false
        val bytes = output.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= maxBytes) return output to false

        val headBytes = 64 * 1024
        val tailBytes = 128 * 1024
        val head = String(bytes, 0, headBytes, StandardCharsets.UTF_8)
        val tailStart = bytes.size - tailBytes
        val tail = String(bytes, tailStart, tailBytes, StandardCharsets.UTF_8)

        val truncatedNote = "\n\n[... Truncated ${bytes.size - (headBytes + tailBytes)} bytes to protect database ...]\n\n"
        return (head + truncatedNote + tail) to true
    }
}
