package com.socialcommentcollector.app.platform.xiaohongshu.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSanitizerTest {
    private val sanitizer = DiagnosticSanitizer(maxSemanticEntries = 2, maxStateKeys = 2, maxTextLength = 24)

    @Test
    fun `removes query fragment and credentials from diagnostic output`() {
        val result = sanitizer.sanitize(
            RawDiagnosticSnapshot(
                url = "https://www.xiaohongshu.com/explore/note?xsec_token=secret#private",
                title = "token=secret-value visible title",
                readyState = "complete",
                elementCount = 12,
                scrollHeight = 1000,
                viewportHeight = 600,
                structuredStateKeys = listOf("__INITIAL_STATE__"),
                semanticEntries = listOf(
                    RawSemanticEntry("H1", null, null, "password=hunter2"),
                ),
            ),
        )

        assertEquals("https://www.xiaohongshu.com/explore/note", result.safeUrl)
        assertFalse(result.toString().contains("secret", ignoreCase = true))
        assertFalse(result.toString().contains("hunter2", ignoreCase = true))
        assertTrue(result.title.contains("[REDACTED]"))
    }

    @Test
    fun `clips and bounds semantic evidence and state keys`() {
        val result = sanitizer.sanitize(
            RawDiagnosticSnapshot(
                url = "about:blank",
                title = "A".repeat(100),
                readyState = "complete",
                elementCount = 5,
                scrollHeight = 400,
                viewportHeight = 400,
                structuredStateKeys = listOf("stateOne", "stateTwo", "stateThree"),
                semanticEntries = listOf(
                    RawSemanticEntry("H1", "heading", null, "B".repeat(100)),
                    RawSemanticEntry("BUTTON", "button", "Load", "Load more"),
                    RawSemanticEntry("ARTICLE", null, null, "must not be retained"),
                ),
            ),
        )

        assertEquals(24, result.title.length)
        assertEquals(listOf("stateOne", "stateTwo"), result.structuredStateKeys)
        assertEquals(2, result.semanticEntries.size)
        assertEquals(24, result.semanticEntries.first().text.length)
    }
}
