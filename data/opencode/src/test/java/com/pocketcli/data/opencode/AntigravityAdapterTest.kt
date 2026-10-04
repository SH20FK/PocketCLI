package com.pocketcli.data.opencode

import com.pocketcli.core.model.*
import com.pocketcli.core.security.AntigravityAuthManager
import com.pocketcli.core.security.SecretStore
import com.pocketcli.data.opencode.antigravity.AntigravityAdapter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AntigravityAdapterTest {

    private lateinit var server: MockWebServer

    private class FakeSecretStore : SecretStore {
        override fun encrypt(plaintext: String): String = "enc_$plaintext"
        override fun decrypt(encryptedPayload: String): String = encryptedPayload.removePrefix("enc_")
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testAntigravityModelsAndCapabilities() = runBlocking {
        val fakeSecret = FakeSecretStore()
        val authManager = object : AntigravityAuthManager(
            secretStore = fakeSecret
        ) {
            override suspend fun getValidAccessToken(clientId: String): Result<String> {
                return Result.success("test-access-token")
            }
        }

        val adapter = AntigravityAdapter(
            authManager = authManager,
            database = null,
            profileId = "profile-test"
        )

        assertTrue(adapter.capabilities.contains(Capability.Streaming))
        assertTrue(adapter.capabilities.contains(Capability.Permissions))

        val models = adapter.getModels().getOrThrow()
        assertTrue(models.any { it.modelId == "gemini-2.5-flash" })
        assertTrue(models.any { it.modelId == "gemini-3.8-flash-high" })
        assertTrue(models.any { it.modelId == "gemini-3.7-flash-high" })
        assertTrue(models.any { it.modelId == "gemini-3.1-pro-high" })
        assertTrue(models.any { it.modelId == "claude-sonnet-4-6" })

        val sessionResult = adapter.createSession("Antigravity Test Session")
        assertTrue(sessionResult.isSuccess)
        val session = sessionResult.getOrThrow()
        assertEquals(AgentType.ANTIGRAVITY, session.agentType)
        assertEquals("Antigravity Test Session", session.title)
    }

    @Test
    fun testAntigravityCancelAndDisconnect() = runBlocking {
        val fakeSecret = FakeSecretStore()
        val authManager = object : AntigravityAuthManager(
            secretStore = fakeSecret
        ) {
            override suspend fun getValidAccessToken(clientId: String): Result<String> {
                return Result.success("test-access-token")
            }
        }

        val adapter = AntigravityAdapter(
            authManager = authManager,
            database = null,
            profileId = "profile-test"
        )

        val cancelRes = adapter.cancel("dummy-session")
        assertTrue(cancelRes.isSuccess)

        adapter.disconnect()
    }

    @Test
    fun testAntigravityBoQSerialization() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val sseChunk = """{"response":{"candidates":[{"content":{"role":"model","parts":[{"text":"Hello from Antigravity","thought":false}]}}]}}"""
        val chunk = json.decodeFromString<com.pocketcli.data.opencode.antigravity.GeminiStreamChunk>(sseChunk)
        val text = chunk.response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        assertEquals("Hello from Antigravity", text)
    }

    @Test
    fun testAntigravityFunctionCallDeserialization() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val chunkJson = """
            {
                "candidates": [
                    {
                        "content": {
                            "role": "model",
                            "parts": [
                                {
                                    "functionCall": {
                                        "name": "web_search",
                                        "args": {
                                            "query": "kotlin coroutines latest release"
                                        }
                                    }
                                }
                            ]
                        },
                        "finishReason": "STOP"
                    }
                ]
            }
        """.trimIndent()

        val chunk = json.decodeFromString<com.pocketcli.data.opencode.antigravity.GeminiStreamChunk>(chunkJson)
        val part = chunk.candidates?.firstOrNull()?.content?.parts?.firstOrNull()
        assertNotNull(part)
        assertNotNull(part?.functionCall)
        assertEquals("web_search", part?.functionCall?.name)
        val query = part?.functionCall?.args?.get("query")
        assertNotNull(query)
    }

    @Test
    fun testAntigravityFunctionResponseSerialization() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        val req = com.pocketcli.data.opencode.antigravity.GeminiGenerateRequest(
            contents = listOf(
                com.pocketcli.data.opencode.antigravity.GeminiContent(
                    role = "function",
                    parts = listOf(
                        com.pocketcli.data.opencode.antigravity.GeminiPart(
                            functionResponse = com.pocketcli.data.opencode.antigravity.GeminiFunctionResponse(
                                name = "web_search",
                                response = kotlinx.serialization.json.buildJsonObject {
                                    put("output", "Kotlin 2.0 released")
                                }
                            )
                        )
                    )
                )
            )
        )

        val serialized = json.encodeToString(req)
        assertTrue(serialized.contains("\"functionResponse\""))
        assertTrue(serialized.contains("\"web_search\""))
        assertTrue(serialized.contains("\"output\":\"Kotlin 2.0 released\""))
    }
}
