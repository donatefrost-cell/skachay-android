package ru.skachay.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpWarningParserTest {
    @Test
    fun capturesWarningsAndJavaScriptChallengeFailuresOnce() {
        val warnings = YtDlpWarningParser.parse(
            """
            [debug] Request options
            [youtube] WARNING: JavaScript runtime is unavailable
            WARNING: No supported JavaScript runtime could be found
            [youtube] n challenge solver failed
            [youtube] Downloading webpage
            """.trimIndent(),
        )

        assertEquals(3, warnings.size)
        assertTrue(warnings[0].contains("JavaScript runtime"))
        assertTrue(warnings[1].contains("JavaScript runtime"))
        assertTrue(warnings[2].contains("n challenge"))
    }

    @Test
    fun redactsMediaUrlsAndSecretValuesFromWarningText() {
        val warning = YtDlpWarningParser.parse(
            "WARNING: challenge failed for https://example.com/watch?v=private&token=secret authorization=BearerSecret",
        ).single()

        assertFalseContains(warning, "private")
        assertFalseContains(warning, "secret")
        assertTrue(warning.contains("[ссылка скрыта]"))
        assertTrue(warning.contains("[скрыто]"))
    }

    @Test
    fun limitsOutputToDistinctShortWarnings() {
        val output = (1..40).joinToString("\n") { "WARNING: repeated warning $it" }
        val warnings = YtDlpWarningParser.parse(output)

        assertEquals(24, warnings.size)
        assertTrue(warnings.all { it.length <= 240 })
    }

    private fun assertFalseContains(value: String, fragment: String) {
        org.junit.Assert.assertFalse(value.contains(fragment, ignoreCase = true))
    }
}
