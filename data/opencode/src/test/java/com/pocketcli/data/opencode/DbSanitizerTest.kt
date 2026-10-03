package com.pocketcli.data.opencode

import com.pocketcli.data.opencode.db.DbSanitizer
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
        // Generate a 1 MB string
        val hugeText = "X".repeat(1024 * 1024)
        val (output, isTruncated) = DbSanitizer.sanitizeOutput(hugeText, maxBytes = 256 * 1024)
        assertTrue(isTruncated)
        assertNotNull(output)
        assertTrue("Output should contain truncation indicator", output!!.contains("Truncated"))
        // Output must be well below 2 MB Android CursorWindow limit
        assertTrue(output.length < 300 * 1024)
    }
}
