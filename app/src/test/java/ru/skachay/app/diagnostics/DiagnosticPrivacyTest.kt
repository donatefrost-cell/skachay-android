package ru.skachay.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class DiagnosticPrivacyTest {
    @Test
    fun errorMessagesNeverIncludeExceptionContents() {
        val secret = "https://example.com/private?token=secret"
        val message = diagnosticError(DownloadProvider.INSTAGRAM, IllegalStateException(secret))
        assertFalse(message.contains(secret))
        assertFalse(message.contains("secret"))
        assertTrue(diagnosticError(DownloadProvider.YOUTUBE, UnknownHostException(secret)).contains("интернет"))
    }

    @Test
    fun copiedLogRedactsSecretsAndHasAHardSizeLimit() {
        val entry =
            DiagnosticEntry(
                provider = DownloadProvider.INSTAGRAM,
                status = DiagnosticStatus.FAILED,
                lastError =
                    """
                    Cookie: session-secret; csrftoken=csrf-secret
                    Authorization: Bearer auth-secret
                    https://example.com/path?token=query-secret
                    """.trimIndent(),
                engineWarnings = listOf("authorization=header-secret&signature=signature-secret ".repeat(2_000)),
            )
        val log = diagnosticLog(listOf(entry))
        assertFalse(log.contains("session-secret"))
        assertFalse(log.contains("csrf-secret"))
        assertFalse(log.contains("auth-secret"))
        assertFalse(log.contains("query-secret"))
        assertFalse(log.contains("header-secret"))
        assertFalse(log.contains("signature-secret"))
        assertTrue(log.length <= MAX_DIAGNOSTIC_LOG_CHARS)
        assertTrue(log.contains("[скрыто]"))
    }
}
