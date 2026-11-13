package ru.skachay.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
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
    fun youtubeErrorsExplainUnavailableFormatsStreamsAndServerFailures() {
        assertTrue(
            diagnosticError(DownloadProvider.YOUTUBE, IOException("Selected format unavailable"))
                .contains("формат", ignoreCase = true),
        )
        assertTrue(
            diagnosticError(DownloadProvider.YOUTUBE, IOException("No media file produced"))
                .contains("поток", ignoreCase = true),
        )
        assertTrue(
            diagnosticError(DownloadProvider.YOUTUBE, IOException("HTTP 503"))
                .contains("сервис", ignoreCase = true),
        )
        assertTrue(
            diagnosticError(DownloadProvider.YOUTUBE, IOException("HTTP Error 403"))
                .contains("поток", ignoreCase = true),
        )
        assertTrue(
            diagnosticError(DownloadProvider.YOUTUBE, IOException("Unsupported URL"))
                .contains("ссыл", ignoreCase = true),
        )
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
                technicalDetails = "HTTP 403 https://cdn.example/video?signature=technical-secret",
                engineWarnings = listOf("authorization=header-secret&signature=signature-secret ".repeat(2_000)),
            )
        val log = diagnosticLog(listOf(entry))
        assertFalse(log.contains("session-secret"))
        assertFalse(log.contains("csrf-secret"))
        assertFalse(log.contains("auth-secret"))
        assertFalse(log.contains("query-secret"))
        assertFalse(log.contains("technical-secret"))
        assertFalse(log.contains("header-secret"))
        assertFalse(log.contains("signature-secret"))
        assertTrue(log.length <= MAX_DIAGNOSTIC_LOG_CHARS)
        assertTrue(log.contains("[скрыто]"))
    }

    @Test
    fun technicalSummaryIncludesCausesWithoutLeakingSignedUrls() {
        val summary = diagnosticTechnicalSummary(IOException("HTTP 403 https://cdn.example/video?signature=private"))

        assertTrue(summary.contains("IOException"))
        assertTrue(summary.contains("HTTP 403"))
        assertFalse(summary.contains("cdn.example"))
        assertFalse(summary.contains("private"))
    }
}
