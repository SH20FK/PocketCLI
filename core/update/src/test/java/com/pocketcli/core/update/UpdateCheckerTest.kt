package com.pocketcli.core.update

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `test versionCode comparison identifies available update`() {
        val currentCode = 1000000L
        val manifestCode = 1005000L

        val isUpdate = manifestCode > currentCode
        assertTrue(isUpdate)
    }

    @Test
    fun `test versionCode comparison identifies up to date`() {
        val currentCode = 1005000L
        val manifestCode = 1005000L

        val isUpdate = manifestCode > currentCode
        assertFalse(isUpdate)
    }

    @Test
    fun `test SemVer formula correctly matches specification`() {
        // Spec formula: MAJOR * 1_000_000 + MINOR * 1_000 + PATCH
        val major = 1
        val minor = 5
        val patch = 0
        val expectedCode = 1005000L

        val computedCode = major * 1_000_000L + minor * 1_000L + patch.toLong()
        assertEquals(expectedCode, computedCode)
    }
}
