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
        assertTrue(models.any { it.modelId == "gemini-2.5-pro" })
        assertTrue(models.any { it.modelId == "gemini-2.5-flash" })
        assertTrue(models.any { it.modelId == "gemini-2.0-flash-thinking" })

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
}
