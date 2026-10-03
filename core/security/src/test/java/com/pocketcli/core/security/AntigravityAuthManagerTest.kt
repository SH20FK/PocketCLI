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
            context = null,
            secretStore = FakeSecretStore()
        )

        val state = authManager.state.value
        assertFalse(state.isAuthenticated)
        assertEquals(AntigravityAuthType.NONE, state.authType)
        assertEquals("gemini-2.5-pro", state.selectedModel)
        assertNull(state.userEmail)
    }

    @Test
    fun testModelSelection() {
        val authManager = AntigravityAuthManager(
            context = null,
            secretStore = FakeSecretStore()
        )

        authManager.setSelectedModel("gemini-2.5-flash")
        assertEquals("gemini-2.5-flash", authManager.state.value.selectedModel)
    }

    @Test
    fun testLogoutResetsState() {
        val authManager = AntigravityAuthManager(
            context = null,
            secretStore = FakeSecretStore()
        )

        authManager.setSelectedModel("gemini-2.0-flash")
        authManager.logout()

        assertFalse(authManager.state.value.isAuthenticated)
        assertEquals(AntigravityAuthType.NONE, authManager.state.value.authType)
        assertNull(authManager.state.value.userEmail)
    }
}
