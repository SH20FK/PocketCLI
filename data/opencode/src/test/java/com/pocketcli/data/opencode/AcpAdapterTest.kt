package com.pocketcli.data.opencode

import com.pocketcli.core.model.*
import com.pocketcli.data.opencode.acp.AcpAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AcpAdapterTest {

    private class FakeAgentAdapter : AgentAdapter {
        override val capabilities: Set<Capability> = setOf(Capability.Streaming)
        var lastPromptSent: Prompt? = null
        var lastSessionCreated: String? = null

        override suspend fun connect(): Result<Unit> = Result.success(Unit)
        override fun events(sessionId: String): Flow<AgentEvent> = emptyFlow()
        override suspend fun createSession(title: String, directory: String?): Result<Session> {
            lastSessionCreated = title
            return Result.success(
                Session(
                    id = "fake-session-1",
                    profileId = "profile-1",
                    title = title,
                    updatedAt = 1000L,
                    createdAt = 1000L,
                    workspaceId = directory
                )
            )
        }
        override suspend fun listSessions(): Result<List<Session>> = Result.success(emptyList())
        override suspend fun sendPrompt(sessionId: String, prompt: Prompt): Result<Unit> {
            lastPromptSent = prompt
            return Result.success(Unit)
        }
        override suspend fun cancel(sessionId: String): Result<Unit> = Result.success(Unit)
        override suspend fun respondPermission(requestId: String, option: PermissionOption): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect() {}
    }

    @Test
    fun testClaudeCodeAdapterConfigurationAndModels() = runBlocking {
        val fake = FakeAgentAdapter()
        val adapter = AcpAdapter(
            agentType = AgentType.CLAUDE_CODE,
            profileId = "profile-claude",
            underlyingAdapter = fake,
            apiKey = "sk-ant-test"
        )

        assertEquals(AgentType.CLAUDE_CODE, adapter.agentType)
        assertTrue(adapter.capabilities.contains(Capability.Streaming))
        assertTrue(adapter.capabilities.contains(Capability.Permissions))

        val models = adapter.getModels().getOrThrow()
        assertTrue(models.any { it.modelId == "claude-3-7-sonnet" })
        assertTrue(models.any { it.modelId == "claude-3-5-sonnet" })

        val sessionResult = adapter.createSession("Claude Refactor")
        assertTrue(sessionResult.isSuccess)
        val session = sessionResult.getOrThrow()
        assertEquals(AgentType.CLAUDE_CODE, session.agentType)
        assertEquals("Claude Refactor", session.title)

        // Verify model injection on sendPrompt
        adapter.sendPrompt(session.id, Prompt(text = "Hello Claude"))
        assertNotNull(fake.lastPromptSent)
        assertEquals("anthropic", fake.lastPromptSent?.model?.providerId)
        assertEquals("claude-3-7-sonnet", fake.lastPromptSent?.model?.modelId)
    }

    @Test
    fun testAntigravityAdapterConfigurationAndModels() = runBlocking {
        val fake = FakeAgentAdapter()
        val adapter = AcpAdapter(
            agentType = AgentType.ANTIGRAVITY,
            profileId = "profile-antigravity",
            underlyingAdapter = fake,
            apiKey = "AIzaSyTest"
        )

        assertEquals(AgentType.ANTIGRAVITY, adapter.agentType)
        val models = adapter.getModels().getOrThrow()
        assertTrue(models.any { it.modelId == "gemini-2.5-pro" })
        assertTrue(models.any { it.modelId == "gemini-2.0-flash" })

        val sessionResult = adapter.createSession("DeepMind Plan")
        assertTrue(sessionResult.isSuccess)
        val session = sessionResult.getOrThrow()
        assertEquals(AgentType.ANTIGRAVITY, session.agentType)

        // Verify model injection on sendPrompt
        adapter.sendPrompt(session.id, Prompt(text = "Solve task"))
        assertNotNull(fake.lastPromptSent)
        assertEquals("google", fake.lastPromptSent?.model?.providerId)
        assertEquals("gemini-2.5-pro", fake.lastPromptSent?.model?.modelId)
    }

    @Test
    fun testAgentTypeResolution() {
        assertEquals(AgentType.OPENCODE, AgentType.fromId("opencode"))
        assertEquals(AgentType.CLAUDE_CODE, AgentType.fromId("claude"))
        assertEquals(AgentType.ANTIGRAVITY, AgentType.fromId("antigravity"))
        assertEquals(AgentType.CODEX, AgentType.fromId("codex"))
        assertEquals(AgentType.OPENCODE, AgentType.fromId("unknown_fallback"))
    }
}
