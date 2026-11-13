package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class YouTubeDirectTransferFallbackTest {
    @Test
    fun interruptedRangeDoesNotRestartTheWholeVideoWithYtDlp() {
        val failure = IncompleteRangeTransferException(IOException("network interrupted"))
        var fallbackUsed = false
        val thrown =
            assertThrows(IncompleteRangeTransferException::class.java) {
                YouTubeDirectTransfer.withFallback(
                    direct = { throw failure },
                    onDirectFailure = {},
                    fallback = { fallbackUsed = true },
                )
            }
        assertSame(failure, thrown)
        assertTrue(!fallbackUsed)
    }

    @Test
    fun directTransferFailureRunsTheFallbackAndReportsTheOriginalError() {
        var observedFailure: IOException? = null
        var fallbackUsed = false

        val result =
            YouTubeDirectTransfer.withFallback(
                direct = { throw IOException("HTTP 403") },
                onDirectFailure = { observedFailure = it },
                fallback = {
                    fallbackUsed = true
                    "fresh extraction result"
                },
            )

        assertEquals("fresh extraction result", result)
        assertEquals("HTTP 403", observedFailure?.message)
        assertTrue(fallbackUsed)
    }

    @Test
    fun fallbackFailureRetainsTheDirectTransferFailureAsSuppressed() {
        val fallbackFailure = IOException("Fresh extraction failed")
        var thrown: Exception? = null

        try {
            YouTubeDirectTransfer.withFallback(
                direct = { throw IOException("Expired direct URL") },
                onDirectFailure = {},
                fallback = { throw fallbackFailure },
            )
        } catch (error: Exception) {
            thrown = error
        }

        assertSame(fallbackFailure, thrown)
        assertEquals(listOf("Expired direct URL"), fallbackFailure.suppressed.map { it.message })
    }
}
