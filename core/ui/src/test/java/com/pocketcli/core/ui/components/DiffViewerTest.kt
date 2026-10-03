package com.pocketcli.core.ui.components

import org.junit.Assert.*
import org.junit.Test

class DiffViewerTest {

    @Test
    fun `test parseUnifiedDiff handles hunk header and offsets correctly`() {
        val diff = """
            diff --git a/Sample.kt b/Sample.kt
            index 1234567..89abcdef 100644
            --- a/Sample.kt
            +++ b/Sample.kt
            @@ -45,4 +50,5 @@
             context line 45
            -removed line 46
            +added line 51
            +added line 52
             context line 47
        """.trimIndent()

        val parsed = parseUnifiedDiff(diff)

        // Headers
        val headers = parsed.filter { it.type == DiffLineType.HEADER }
        assertTrue(headers.any { it.content.startsWith("---") })
        assertTrue(headers.any { it.content.startsWith("+++") })
        assertTrue(headers.any { it.content.startsWith("@@") })

        // No added/removed lines from file headers
        val addedLines = parsed.filter { it.type == DiffLineType.ADDED }
        assertEquals(2, addedLines.size)
        assertEquals("added line 51", addedLines[0].content.removePrefix("+"))
        assertEquals(51, addedLines[0].newLineNumber)
        assertNull(addedLines[0].oldLineNumber)

        assertEquals("added line 52", addedLines[1].content.removePrefix("+"))
        assertEquals(52, addedLines[1].newLineNumber)
        assertNull(addedLines[1].oldLineNumber)

        val removedLines = parsed.filter { it.type == DiffLineType.REMOVED }
        assertEquals(1, removedLines.size)
        assertEquals("removed line 46", removedLines[0].content.removePrefix("-"))
        assertEquals(46, removedLines[0].oldLineNumber)
        assertNull(removedLines[0].newLineNumber)

        val contextLines = parsed.filter { it.type == DiffLineType.CONTEXT }
        assertEquals(2, contextLines.size)
        assertEquals(45, contextLines[0].oldLineNumber)
        assertEquals(50, contextLines[0].newLineNumber)

        assertEquals(47, contextLines[1].oldLineNumber)
        assertEquals(53, contextLines[1].newLineNumber)
    }

    @Test
    fun `test parseUnifiedDiff handles multiple hunks`() {
        val diff = """
            @@ -10,2 +10,2 @@
            -old10
            +new10
             ctx11
            @@ -100,2 +100,2 @@
            -old100
            +new100
             ctx101
        """.trimIndent()

        val parsed = parseUnifiedDiff(diff)
        val removed = parsed.filter { it.type == DiffLineType.REMOVED }
        assertEquals(2, removed.size)
        assertEquals(10, removed[0].oldLineNumber)
        assertEquals(100, removed[1].oldLineNumber)

        val added = parsed.filter { it.type == DiffLineType.ADDED }
        assertEquals(2, added.size)
        assertEquals(10, added[0].newLineNumber)
        assertEquals(100, added[1].newLineNumber)
    }
}
