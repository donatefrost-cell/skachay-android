package ru.skachay.app.diagnostics

import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticPrivacyTest {
    @Test
    fun errorMessagesNeverIncludeExceptionContents() {
        val secret = "https://example.com/private?token=secret"
        val message = diagnosticError(DownloadProvider.INSTAGRAM, IllegalStateException(secret))
        assertFalse(message.contains(secret))
        assertFalse(message.contains("secret"))
        assertTrue(diagnosticError(DownloadProvider.YOUTUBE, UnknownHostException(secret)).contains("интернет"))
    }
}
