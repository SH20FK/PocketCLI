package com.pocketcli.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AntigravityAuthManagerTest {

    private class FakeSecretStore : SecretStore {
        override fun encrypt(plaintext: String): String = "enc_$plaintext"
        override fun decrypt(encryptedPayload: String): String = encryptedPayload.removePrefix("enc_")
    }

    @Test
    fun testDefaultAuthStateUnauthenticated() {
        val authManager = AntigravityAuthManager(
            secretStore = FakeSecretStore()
        )

        val state = authManager.state.value
        assertFalse(state.isAuthenticated)
        assertEquals(AntigravityAuthType.NONE, state.authType)
        assertEquals(AntigravityAuthManager.DEFAULT_MODEL, state.selectedModel)
        assertNull(state.userEmail)
    }

    @Test
    fun testModelSelection() {
        val authManager = AntigravityAuthManager(
            secretStore = FakeSecretStore()
        )

        authManager.setSelectedModel("gemini-3.7-flash-high")
        assertEquals("gemini-3.7-flash-high", authManager.state.value.selectedModel)
    }

    @Test
    fun testLogoutResetsState() {
        val authManager = AntigravityAuthManager(
            secretStore = FakeSecretStore()
        )

        authManager.setSelectedModel("gemini-3.1-pro-high")
        authManager.logout()

        assertFalse(authManager.state.value.isAuthenticated)
        assertEquals(AntigravityAuthType.NONE, authManager.state.value.authType)
        assertNull(authManager.state.value.userEmail)
    }

    @Test
    fun testGoogleAuthUrlFormat() {
        val authManager = AntigravityAuthManager(secretStore = FakeSecretStore())
        val url = authManager.getGoogleAuthUrl()
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(url.contains("client_id="))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("pocketcli%3A%2F%2Fauth"))
    }

    @Test
    fun testImportApiKey() = runBlocking {
        val authManager = AntigravityAuthManager(secretStore = FakeSecretStore())
        val res = authManager.importTokenOrCode("AIzaSyFakeGeminiApiKey12345")
        assertTrue(res.isSuccess)
    }
}
