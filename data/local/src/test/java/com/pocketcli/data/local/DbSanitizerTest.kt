package com.pocketcli.data.local

import com.pocketcli.data.local.db.DbSanitizer
import org.junit.Assert.*
import org.junit.Test

class DbSanitizerTest {

    @Test
    fun testNormalOutputNotTruncated() {
        val normalText = "All files compiled successfully."
        val (output, isTruncated) = DbSanitizer.sanitizeOutput(normalText)
        assertEquals(normalText, output)
        assertFalse(isTruncated)
    }

    @Test
    fun testHugeOutputTruncatedSafely() {
        val hugeText = "X".repeat(1024 * 1024)
        val (output, isTruncated) = DbSanitizer.sanitizeOutput(hugeText, maxBytes = 256 * 1024)
        assertTrue(isTruncated)
        assertNotNull(output)
        assertTrue("Output should contain truncation indicator", output!!.contains("Truncated"))
        assertTrue(output.length < 300 * 1024)
    }
}
