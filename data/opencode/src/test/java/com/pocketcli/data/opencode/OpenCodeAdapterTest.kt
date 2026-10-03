package com.pocketcli.data.opencode

import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds
import com.pocketcli.core.model.ModelIdentifier
import com.pocketcli.core.model.PermissionOption
import com.pocketcli.core.model.Prompt
import com.pocketcli.core.model.SessionState
import com.pocketcli.core.model.ToolStatus
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.api.CleartextHttpPolicyInterceptor
import com.pocketcli.data.opencode.api.OpenCodeApiClient
import com.pocketcli.data.opencode.sse.OpenCodeSseClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStreamReader

class OpenCodeAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var apiClient: OpenCodeApiClient
    private lateinit var sseClient: OpenCodeSseClient
    private lateinit var adapter: OpenCodeAdapter

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient.Builder().build()
        val baseUrl = mockWebServer.url("/").toString()

        apiClient = OpenCodeApiClient(
            okHttpClient = okHttpClient,
            baseUrlProvider = { baseUrl }
        )

        sseClient = OpenCodeSseClient(
            baseOkHttpClient = okHttpClient,
            baseUrlProvider = { baseUrl }
        )

        adapter = OpenCodeAdapter(
            apiClient = apiClient,
            sseClient = sseClient,
            profileId = "profile_test"
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testHealthCheckParsing() = runBlocking {
        val healthJson = """{"healthy":true,"version":"1.2.27"}"""
        mockWebServer.enqueue(MockResponse().setBody(healthJson).setResponseCode(200))

        val result = adapter.connect()
        assertTrue("Expected connect to succeed", result.isSuccess)
    }

    @Test
    fun testCreateSession() = runBlocking {
        val sessionJson = """
            {
              "id": "ses_test_123",
              "slug": "mighty-garden",
              "version": "1.2.27",
              "title": "Unit Test Session",
              "time": {
                "created": 1791001747914,
                "updated": 1791001747914
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(sessionJson).setResponseCode(200))

        val result = adapter.createSession("Unit Test Session")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("ses_test_123", session.id)
        assertEquals("profile_test", session.profileId)
        assertEquals("Unit Test Session", session.title)
    }

    @Test
    fun testSseFullTurnFlow() = runBlocking {
        val sseStream = """
            data: {"payload":{"type":"server.connected","properties":{}}}

            data: {"payload":{"type":"session.status","properties":{"sessionID":"ses_test_123","status":{"type":"busy"}}}}

            data: {"payload":{"type":"message.part.delta","properties":{"sessionID":"ses_test_123","messageID":"msg_1","partID":"prt_1","field":"text","delta":"Hello world"}}}

            data: {"payload":{"type":"message.part.updated","properties":{"part":{"id":"prt_tool_1","sessionID":"ses_test_123","messageID":"msg_1","type":"tool","callID":"call_1","tool":"bash","state":{"status":"completed","output":"done"}}}}}

            data: {"payload":{"type":"permission.asked","properties":{"id":"per_1","sessionID":"ses_test_123","permission":"bash","patterns":["ls"],"always":["ls"],"tool":{"messageID":"msg_1","callID":"call_1"}}}}

            data: {"payload":{"type":"session.idle","properties":{"sessionID":"ses_test_123"}}}

        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseStream + "\n\n")
        )

        adapter.events("ses_test_123").test(timeout = 10.seconds) {
            // 1. SessionStatus BUSY
            val e1 = awaitItem()
            assertTrue(e1 is com.pocketcli.core.model.AgentEvent.SessionStatus)
            assertEquals(SessionState.BUSY, (e1 as com.pocketcli.core.model.AgentEvent.SessionStatus).state)

            // 2. TextDelta
            val e2 = awaitItem()
            assertTrue(e2 is com.pocketcli.core.model.AgentEvent.TextDelta)
            assertEquals("Hello world", (e2 as com.pocketcli.core.model.AgentEvent.TextDelta).text)

            // 3. ToolCallUpdate
            val e3 = awaitItem()
            assertTrue(e3 is com.pocketcli.core.model.AgentEvent.ToolCallUpdate)
            assertEquals(ToolStatus.COMPLETED, (e3 as com.pocketcli.core.model.AgentEvent.ToolCallUpdate).status)
            assertEquals("done", (e3 as com.pocketcli.core.model.AgentEvent.ToolCallUpdate).output)

            // 4. PermissionRequested
            val e4 = awaitItem()
            assertTrue(e4 is com.pocketcli.core.model.AgentEvent.PermissionRequested)
            assertEquals("per_1", (e4 as com.pocketcli.core.model.AgentEvent.PermissionRequested).requestId)

            // 5. SessionStatus IDLE
            val e5 = awaitItem()
            assertTrue(e5 is com.pocketcli.core.model.AgentEvent.SessionStatus)
            assertEquals(SessionState.IDLE, (e5 as com.pocketcli.core.model.AgentEvent.SessionStatus).state)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test(expected = SecurityException::class)
    fun testCleartextBlocking() {
        var allowCleartext = false
        val clientWithCleartextCheck = OkHttpClient.Builder()
            .addInterceptor(CleartextHttpPolicyInterceptor { allowCleartext })
            .build()

        val request = okhttp3.Request.Builder()
            .url("http://192.168.1.100:4096/global/health")
            .build()

        clientWithCleartextCheck.newCall(request).execute()
    }

    @Test
    fun testGetModels() = runBlocking {
        val providersJson = """
            {
              "all": [
                {
                  "id": "opencode",
                  "name": "OpenCode",
                  "models": {
                    "claude-sonnet-4-6": {
                      "id": "claude-sonnet-4-6",
                      "name": "Claude 3.7 Sonnet"
                    }
                  }
                }
              ],
              "connected": ["opencode"],
              "default": {"opencode": "claude-sonnet-4-6"}
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(providersJson).setResponseCode(200))

        val result = adapter.getModels()
        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals(1, models.size)
        assertEquals("opencode", models[0].providerId)
        assertEquals("claude-sonnet-4-6", models[0].modelId)
        assertEquals("Claude 3.7 Sonnet", models[0].name)
    }

    @Test
    fun testSendMessageWithoutModel() = runBlocking {
        val dummyResponse = """{"info":{"id":"msg_1","sessionID":"ses_test_123","role":"assistant","time":{"created":100,"completed":200}},"parts":[]}"""
        mockWebServer.enqueue(MockResponse().setBody(dummyResponse).setResponseCode(200))

        val result = adapter.sendPrompt("ses_test_123", Prompt(text = "Hello world"))
        assertTrue(result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertEquals("/session/ses_test_123/message", recordedRequest.path)
        assertTrue("Request body should contain text part", requestBody.contains("\"text\":\"Hello world\""))
        assertFalse("Request body must NOT contain null model field", requestBody.contains("\"model\""))
    }

    @Test
    fun testSendMessageWithModel() = runBlocking {
        val dummyResponse = """{"info":{"id":"msg_1","sessionID":"ses_test_123","role":"assistant","time":{"created":100,"completed":200}},"parts":[]}"""
        mockWebServer.enqueue(MockResponse().setBody(dummyResponse).setResponseCode(200))

        val result = adapter.sendPrompt(
            "ses_test_123",
            Prompt(text = "Hello world", model = ModelIdentifier("opencode", "claude-sonnet-4-6"))
        )
        assertTrue(result.isSuccess)

        val recordedRequest = mockWebServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertEquals("/session/ses_test_123/message", recordedRequest.path)
        assertTrue("Request body should contain text part", requestBody.contains("\"text\":\"Hello world\""))
        assertTrue("Request body must contain model object", requestBody.contains("\"model\":{\"providerID\":\"opencode\",\"modelID\":\"claude-sonnet-4-6\"}"))
    }
}
